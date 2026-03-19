package com.rentflow.user_service.service.keycloak.impl;

import com.rentflow.user_service.dto.user.request.UserRegisterRequestDto;
import com.rentflow.user_service.dto.user.request.UserUpdateRequestDto;
import com.rentflow.user_service.exception.UserAlreadyExistsException;
import com.rentflow.user_service.service.keycloak.contract.IdentityProviderService;
import com.rentflow.user_service.service.keycloak.contract.IdentityUserService;
import jakarta.ws.rs.core.Response;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;


//TODO: This service is currently tightly coupled with Keycloak's admin client API. Consider abstracting it further to allow for easier testing and potential future changes in the identity provider.
//TODO: Add more comprehensive error handling and logging throughout the service methods.
//TODO: Implement caching for user retrieval methods to reduce load on Keycloak and improve performance.
//TODO: Refactor methods to return more meaningful responses or custom exceptions instead of generic RuntimeExceptions.
//TODO Refactor methods to use a more functional style with Optional and Result types where appropriate, especially for methods that may fail or return null values.
@Service
@RequiredArgsConstructor
public class KeycloakUserServiceImpl implements IdentityUserService {

    private final IdentityProviderService identityProviderService;

    public String createUser(UserRegisterRequestDto request) {

        UserRepresentation user = getUserRepresentation(request);

        try (Response response = identityProviderService.getUsersResource().create(user)) {
            if (response.getStatus() == 409) {
                throw new UserAlreadyExistsException("User with this email already exists");
            }
            if (response.getStatus() != 201) {
                throw new RuntimeException("Failed to create user: " + response.getStatus());
            }

            String locationHeader = response.getLocation().toString();
            return locationHeader.substring(locationHeader.lastIndexOf("/") + 1);
        }
    }

    private static @NonNull UserRepresentation getUserRepresentation(UserRegisterRequestDto request) {
        UserRepresentation user = new UserRepresentation();
        user.setEmail(request.personalInfo().email());
        user.setFirstName(request.personalInfo().firstName());
        user.setLastName(request.personalInfo().lastName());
        user.setEnabled(true);
        user.setEmailVerified(false);

        CredentialRepresentation credential = new CredentialRepresentation();
        credential.setType(CredentialRepresentation.PASSWORD);
        credential.setValue(request.password());
        credential.setTemporary(false);

        user.setCredentials(List.of(credential));
        return user;
    }

    public UserRepresentation getUserById(String userId) {
        return identityProviderService.getUsersResource()
                .get(userId)
                .toRepresentation();
    }

    public Optional<UserRepresentation> getUserByEmail(String email) {
        return identityProviderService.getUsersResource()
                .searchByEmail(email, true)
                .stream()
                .findFirst();
    }

    //TODO: current dto is not finalized
    public void updateUser(String userId, UserUpdateRequestDto request) {
        UserResource userResource = identityProviderService.getUsersResource().get(userId);
        UserRepresentation user = userResource.toRepresentation();
        user.setFirstName(request.firstName());
        user.setLastName(request.lastName());
        userResource.update(user);
    }

    public void deleteUser(String userId) {
        identityProviderService.getUsersResource()
                .get(userId)
                .remove();
    }

    public void assignRole(String userId, String roleName) {
        UserResource userResource = identityProviderService.getUsersResource().get(userId);

        RoleRepresentation role = identityProviderService.getRealmResource()
                .roles()
                .get(roleName)
                .toRepresentation();

        userResource.roles().realmLevel().add(List.of(role));
    }

    public void sendVerificationEmail(String userId) {
        identityProviderService.getUsersResource()
                .get(userId)
                .sendVerifyEmail();
    }

    public void resetPassword(String userId, String newPassword) {
        CredentialRepresentation credential = new CredentialRepresentation();
        credential.setType(CredentialRepresentation.PASSWORD);
        credential.setValue(newPassword);
        credential.setTemporary(false);

        identityProviderService.getUsersResource()
                .get(userId)
                .resetPassword(credential);
    }

    private void setUserEnabled(String keycloakId, boolean enabled) {
        UserResource userResource = identityProviderService.getUsersResource().get(keycloakId);
        UserRepresentation user = userResource.toRepresentation();
        user.setEnabled(enabled);
        userResource.update(user);
    }

    public void disableUser(String keycloakId) {
        setUserEnabled(keycloakId, false);
        identityProviderService.getUsersResource().get(keycloakId).logout();
    }

    public void enableUser(String keycloakId) {
        setUserEnabled(keycloakId, true);
    }

}
