package com.rentflow.user_service.service.impl;

import com.rentflow.user_service.dto.user.request.UpdatePasswordRequest;
import com.rentflow.user_service.dto.user.request.UserRegisterRequestDto;
import com.rentflow.user_service.dto.user.response.UserResponseDto;
import com.rentflow.user_service.entity.PersonalInformation;
import com.rentflow.user_service.entity.User;
import com.rentflow.user_service.entity.enums.UserRole;
import com.rentflow.user_service.entity.enums.UserStatus;
import com.rentflow.user_service.exception.InvalidPasswordException;
import com.rentflow.user_service.exception.UserNotFoundException;
import com.rentflow.user_service.exception.UserRegistrationException;
import com.rentflow.user_service.mapper.UserMapper;
import com.rentflow.user_service.repository.UserRepository;
import com.rentflow.user_service.service.contract.CurrentUserService;
import com.rentflow.user_service.service.contract.UserService;
import com.rentflow.user_service.service.keycloak.contract.IdentityAuthService;
import com.rentflow.user_service.service.keycloak.contract.IdentityUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

//TODO: move exception messages to exception/messages file
//TODO: refactor exceptions
//TODO: add more logging
//TODO: add test coverage
//TODO: add caching for getCurrentUser
//TODO: add metrics for registration, deletion, password updates
//TODO: event publishing for user registration and deletion
//TODO: refactor to use a more functional style with Optional and Result types
@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;

    private final UserMapper userMapper;

    private final CurrentUserService currentUserService;

    private final IdentityAuthService identityAuthService;

    private final IdentityUserService identityUserService;

    @Override
    @Transactional
    public UserResponseDto registerUser(UserRegisterRequestDto request) {
        String keycloakId = identityUserService.createUser(request);
        try {
            User user = userRepository.save(
                    User.builder()
                            .keycloakId(keycloakId)
                            .personalInformation(PersonalInformation.builder().build())
                            .status(UserStatus.REGISTERED)
                            .role(UserRole.USER)
                            .build()
            );
            return userMapper.toResponseDto(user);
        } catch (Exception e) {
            log.error("DB save failed for keycloakId: {}. Rolling back Keycloak user.", keycloakId, e);
            identityUserService.deleteUser(keycloakId);
            throw new UserRegistrationException("Registration failed, please try again", e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponseDto getCurrentUser() {
        String keycloakId = currentUserService.getUserId();
        return userMapper.toResponseDto(
                userRepository.findByKeycloakId(keycloakId)
                        .orElseThrow(() -> new UserNotFoundException("User not found"))
        );
    }

    @Override
    public void updatePassword(UpdatePasswordRequest request) {
        String keycloakId = currentUserService.getUserId();
        String email = currentUserService.getEmail();

        if (!identityAuthService.verifyCurrentPassword(email, request.currentPassword())) {
            throw new InvalidPasswordException("Current password is incorrect");
        }

        identityUserService.resetPassword(keycloakId, request.newPassword());
    }

    @Override
    @Transactional
    public void deleteCurrentUser() {
        String keycloakId = currentUserService.getUserId();

        identityUserService.disableUser(keycloakId);

        try {
            userRepository.findByKeycloakId(keycloakId)
                    .ifPresent(user -> {
                        user.setStatus(UserStatus.INACTIVE);
                        user.setDeactivatedAt(LocalDateTime.now());
                    });
        } catch (Exception e) {
            log.error("DB update failed for keycloakId: {}. Re-enabling Keycloak user.", keycloakId, e);
            identityUserService.enableUser(keycloakId);
            throw new RuntimeException("Failed to deactivate user", e);
        }
    }

    @Override
    @Transactional
    public void hardDeleteUser(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + userId));

        String keycloakId = user.getKeycloakId();

        userRepository.deleteById(userId);

        try {
            identityUserService.deleteUser(keycloakId);
        } catch (Exception e) {
            log.error("CRITICAL: User {} deleted from DB but Keycloak delete failed for keycloakId: {}. Manual cleanup required.", userId, keycloakId, e);
            throw new RuntimeException("Failed to fully delete user", e);
        }
    }

}
