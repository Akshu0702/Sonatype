package com.platform.tenants.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "platform")
public record PlatformProperties(
        /** Base domain; tenants live at {name}.{domain}. */
        String domain,
        /** Browser-facing Keycloak URL, e.g. https://auth.example.com. Becomes the token issuer prefix. */
        String authServerUrl,
        /** Container-network Keycloak URL used for server-to-server calls and JWKS fetches. */
        String keycloakInternalUrl,
        String automationClientId,
        String automationClientSecret,
        /** Master-realm clients allowed to call the tenant management API. */
        List<String> adminClients,
        /** Master-realm public client of the operator console (people signing in with a browser). */
        String operatorClientId,
        /** Realm role a person needs, in addition to coming through that client. */
        String operatorRole) {
}
