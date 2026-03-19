package com.rentflow.user_service.service.keycloak.impl;

import com.rentflow.user_service.service.keycloak.contract.IdentityAuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

@Service
@RequiredArgsConstructor
public class KeycloakAuthServiceImpl implements IdentityAuthService {

    @Value("${keycloak.server-url}")
    private String serverUrl;

    @Value("${keycloak.target-realm}")
    private String targetRealm;

    @Value("${keycloak.client-id}")
    private String clientId;

    @Value("${keycloak.client-secret}")
    private String clientSecret;

    private final RestClient restClient;

    public boolean verifyCurrentPassword(String email, String currentPassword) {
        try {
            restClient.post()
                .uri(serverUrl + "/realms/" + targetRealm + "/protocol/openid-connect/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(buildTokenRequest(email, currentPassword))
                .retrieve()
                .toBodilessEntity();

            return true;
        } catch (HttpClientErrorException e) {
            return false;
        }
    }

    private MultiValueMap<String, String> buildTokenRequest(String email, String password) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "password");
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        form.add("password", email);
        form.add("password", password);
        return form;
    }

}
