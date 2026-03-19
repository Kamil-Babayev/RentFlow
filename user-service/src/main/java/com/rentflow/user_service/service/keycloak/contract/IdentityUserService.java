package com.rentflow.user_service.service.keycloak.contract;

import com.rentflow.user_service.dto.user.request.UserRegisterRequestDto;
import com.rentflow.user_service.dto.user.request.UserUpdateRequestDto;
import org.keycloak.representations.idm.UserRepresentation;

import java.util.Optional;

public interface IdentityUserService {

    String createUser(UserRegisterRequestDto request);

    UserRepresentation getUserById(String userId);

    Optional<UserRepresentation> getUserByEmail(String email);

    void updateUser(String userId, UserUpdateRequestDto request);

    void deleteUser(String userId);

    void assignRole(String userId, String roleName);

    void sendVerificationEmail(String userId);

    void resetPassword(String userId, String newPassword);

    void disableUser(String keycloakId);

    void enableUser(String keycloakId);

}
