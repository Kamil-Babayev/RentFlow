package com.rentflow.user_service.service.keycloak;

import com.rentflow.user_service.dto.user.request.UserRegisterRequestDto;
import com.rentflow.user_service.dto.user.request.UserUpdateRequestDto;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class KeycloakUserService {

    private final KeycloakProviderService keycloakProviderService;

    public void createUser(UserRegisterRequestDto request) {

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

        try (Response response = keycloakProviderService.getUsersResource().create(user)) {
            if (response.getStatus() != 201) {
                throw new RuntimeException("Failed to create user: " + response.getStatus());
            }
        }
    }

    public UserRepresentation getUserById(String userId) {
        return keycloakProviderService.getUsersResource()
                .get(userId)
                .toRepresentation();
    }

    public Optional<UserRepresentation> getUserByEmail(String email) {
        return keycloakProviderService.getUsersResource()
                .searchByEmail(email, true)
                .stream()
                .findFirst();
    }

    //TODO: current dto is not finalized
    public void updateUser(String userId, UserUpdateRequestDto request) {
        UserRepresentation user = getUserById(userId);
        user.setFirstName(request.firstName());
        user.setLastName(request.lastName());

        keycloakProviderService.getUsersResource()
                .get(userId)
                .update(user);
    }

    public void deleteUser(String userId) {
        keycloakProviderService.getUsersResource()
                .get(userId)
                .remove();
    }

    public void assignRole(String userId, String roleName) {
        RoleRepresentation role = keycloakProviderService.getRealmResource()
                .roles()
                .get(roleName)
                .toRepresentation();

        keycloakProviderService.getUsersResource()
                .get(userId)
                .roles()
                .realmLevel()
                .add(List.of(role));
    }

    public void sendVerificationEmail(String userId) {
        keycloakProviderService.getUsersResource()
                .get(userId)
                .sendVerifyEmail();
    }

    public void resetPassword(String userId, String newPassword) {
        CredentialRepresentation credential = new CredentialRepresentation();
        credential.setType(CredentialRepresentation.PASSWORD);
        credential.setValue(newPassword);
        credential.setTemporary(false);

        keycloakProviderService.getUsersResource()
                .get(userId)
                .resetPassword(credential);
    }

    public String getUserId() {
        return getJwt().getSubject(); // Keycloak user ID
    }

    public String getEmail() {
        return getJwt().getClaimAsString("email");
    }

    public String getFirstName() {
        return getJwt().getClaimAsString("given_name");
    }

    public String getLastName() {
        return getJwt().getClaimAsString("family_name");
    }

    public List<String> getRoles() {
        Map<String, Object> realmAccess = getJwt().getClaimAsMap("realm_access");
        if (realmAccess == null) return List.of();
        return (List<String>) realmAccess.getOrDefault("roles", List.of());
    }

    private Jwt getJwt() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (Jwt) auth.getPrincipal();
    }

}
