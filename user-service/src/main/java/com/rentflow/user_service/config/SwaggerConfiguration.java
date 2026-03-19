package com.rentflow.user_service.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.OAuthFlow;
import io.swagger.v3.oas.models.security.OAuthFlows;
import io.swagger.v3.oas.models.security.Scopes;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfiguration {

    @Value("${keycloak.server-url}")
    private String serverUrl;

    @Value("${keycloak.target-realm}")
    private String targetRealm;

    @Bean
    public OpenAPI openAPI() {
        String authUrl = serverUrl + "/realms/" + targetRealm + "/protocol/openid-connect";

        return new OpenAPI()
                .info(new Info()
                        .title("Your App API")
                        .description("API documentation")
                        .version("1.0.0"))
                .addSecurityItem(new SecurityRequirement().addList("Keycloak"))
                .components(new Components()
                        .addSecuritySchemes("Keycloak", new SecurityScheme()
                                .type(SecurityScheme.Type.OAUTH2)
                                .flows(new OAuthFlows()
                                        .authorizationCode(new OAuthFlow()
                                                .authorizationUrl(authUrl + "/auth")
                                                .tokenUrl(authUrl + "/token")
                                                .scopes(new Scopes()
                                                        .addString("openid", "OpenID Connect")
                                                        .addString("profile", "Profile")
                                                        .addString("email", "Email")
                                                )
                                        )
                                )
                        )
                );
    }

}
