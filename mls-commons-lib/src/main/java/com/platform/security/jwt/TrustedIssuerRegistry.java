package com.platform.security.jwt;

import java.util.Optional;

/**
 * Where a service learns which issuers it trusts. The Tenants service implements
 * this over its own tables; every other service implements it by asking Tenants.
 * Implementations must match the issuer string EXACTLY (no prefix or substring
 * matching: PLATFORM-SPEC §1).
 */
public interface TrustedIssuerRegistry {

    Optional<TrustedIssuer> find(String issuer);
}
