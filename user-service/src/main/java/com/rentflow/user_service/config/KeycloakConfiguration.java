package com.rentflow.user_service.config;

import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class KeycloakConfiguration {

    private String serverUrl;

    private String realm;

    private String clientId;

    private String clientSecret;

    @Bean
    public Keycloak keycloak() {
        //TODO: Configure Keycloak client with appropriate settings
        return KeycloakBuilder.builder()
                .build();
    }

}
