package com.platform.tenants.resolve;

public record TenantConfig(
        String authServerUrl,
        String realmName,
        String clientId,
        String issuerUri,
        String jwkUri,
        String tokenEndpoint) {
}
