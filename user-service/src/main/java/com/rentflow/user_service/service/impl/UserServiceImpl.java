package com.rentflow.user_service.service.impl;

import com.rentflow.user_service.dto.user.request.UpdatePasswordRequest;
import com.rentflow.user_service.dto.user.request.UserRegisterRequestDto;
import com.rentflow.user_service.dto.user.response.UserResponseDto;
import com.rentflow.user_service.exception.InvalidPasswordException;
import com.rentflow.user_service.exception.UserNotFoundException;
import com.rentflow.user_service.service.contract.UserService;
import com.rentflow.user_service.service.keycloak.KeycloakAuthService;
import com.rentflow.user_service.service.keycloak.KeycloakUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.representations.idm.AbstractUserRepresentation;
import org.springframework.stereotype.Service;

import java.util.UUID;

//TODO: move exception messages to exception/messages file
@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final KeycloakAuthService keycloakAuthService;

    private final KeycloakUserService keycloakUserService;

    @Override
    public UserResponseDto registerUser(UserRegisterRequestDto request) {
        return null;
    }

    @Override
    public UserResponseDto getCurrentUser() {
        return null;
    }

    @Override
    public void updatePassword(UpdatePasswordRequest request) {

        String userMail = keycloakUserService.getEmail();

        boolean isValid = keycloakAuthService.verifyCurrentPassword(userMail, request.currentPassword());

        if (!isValid) {
            throw new InvalidPasswordException("Current password is incorrect");
        }

        String userId = keycloakUserService.getUserByEmail(keycloakUserService.getEmail())
                .map(AbstractUserRepresentation::getId)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        keycloakUserService.resetPassword(userId, request.newPassword());
    }

    @Override
    public void deleteCurrentUser() {

    }

    @Override
    public void hardDeleteUser(UUID userId) {

    }

}
