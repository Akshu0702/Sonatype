package com.platform.tenants.audit;

import org.springframework.security.oauth2.jwt.Jwt;

import java.util.UUID;

/** Who performed a platform operation: a person signed in to the console, or an automation client. */
public record Operator(UUID id, String name) {

    public static Operator of(Jwt jwt) {
        UUID id;
        try {
            id = UUID.fromString(jwt.getSubject());
        } catch (RuntimeException e) {
            id = new UUID(0, 0);
        }
        String name = jwt.getClaimAsString("email");
        if (name == null || name.isBlank()) {
            name = jwt.getClaimAsString("preferred_username");
        }
        return new Operator(id, name == null || name.isBlank() ? "platform" : name);
    }
}
