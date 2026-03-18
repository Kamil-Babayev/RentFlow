package com.rentflow.user_service.service.keycloak;

import lombok.RequiredArgsConstructor;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class KeycloakProviderService {

    @Value("${keycloak.target-realm}")
    private String targetRealm;

    private final Keycloak keycloak;

    public RealmResource getRealmResource() {
        return keycloak.realm(targetRealm);
    }

    public UsersResource getUsersResource() {
        return getRealmResource().users();
    }

}
