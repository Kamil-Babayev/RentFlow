package com.rentflow.user_service.service.impl;

import com.rentflow.user_service.exception.UnauthorizedException;
import com.rentflow.user_service.service.contract.CurrentUserService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class CurrentUserServiceImpl implements CurrentUserService {
    public String getUserId() {
        return getJwt().getSubject();
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

    @SuppressWarnings("unchecked")
    public List<String> getRoles() {
        Map<String, Object> realmAccess = getJwt().getClaimAsMap("realm_access");
        if (realmAccess == null) return List.of();
        return (List<String>) realmAccess.getOrDefault("roles", List.of());
    }

    private Jwt getJwt() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new UnauthorizedException("No authenticated user found");
        }
        if (!(auth.getPrincipal() instanceof Jwt jwt)) {
            throw new UnauthorizedException("Invalid authentication token");
        }
        return jwt;
    }

}
