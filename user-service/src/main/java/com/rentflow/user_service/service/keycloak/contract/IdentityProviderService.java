package com.rentflow.user_service.service.keycloak.contract;

import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UsersResource;

public interface IdentityProviderService {

    RealmResource getRealmResource();

    UsersResource getUsersResource();

}
