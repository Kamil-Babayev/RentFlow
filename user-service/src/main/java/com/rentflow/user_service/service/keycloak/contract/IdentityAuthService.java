package com.rentflow.user_service.service.keycloak.contract;

public interface IdentityAuthService {

    boolean verifyCurrentPassword(String email, String currentPassword);

}
