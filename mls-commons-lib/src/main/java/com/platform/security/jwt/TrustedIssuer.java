package com.platform.security.jwt;

import java.util.UUID;

/**
 * A realm the platform trusts to sign tokens, and the tenant it belongs to.
 * The tenant is fixed by this registration, never by a claim in the token.
 */
public record TrustedIssuer(String issuer, String jwkSetUri, UUID tenantId) {
}
