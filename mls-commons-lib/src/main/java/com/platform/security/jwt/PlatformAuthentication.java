package com.platform.security.jwt;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collection;

/**
 * The authenticated principal after cryptographic verification. Authorities start
 * empty: permissions come from the platform's own RBAC tables (spec §4.1), added by
 * the IAM integration, never from the identity provider's role or group claims.
 */
public class PlatformAuthentication extends AbstractAuthenticationToken {

    private final PlatformPrincipal principal;
    private final Jwt jwt;

    public PlatformAuthentication(PlatformPrincipal principal, Jwt jwt, Collection<? extends GrantedAuthority> authorities) {
        super(authorities);
        this.principal = principal;
        this.jwt = jwt;
        setAuthenticated(true);
    }

    @Override
    public PlatformPrincipal getPrincipal() {
        return principal;
    }

    @Override
    public Jwt getCredentials() {
        return jwt;
    }
}
