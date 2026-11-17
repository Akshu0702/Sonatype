package com.platform.security.tenancy;

import org.hibernate.context.spi.CurrentTenantIdentifierResolver;

import java.util.UUID;

/** Tells Hibernate which tenant a new session belongs to, from {@link TenantContext}. */
public class TenantIdentifierResolver implements CurrentTenantIdentifierResolver<String> {

    /** Sentinel for "no tenant": the connection provider turns it into an unset session variable. */
    public static final String NO_TENANT = "no-tenant";

    @Override
    public String resolveCurrentTenantIdentifier() {
        return TenantContext.current().map(UUID::toString).orElse(NO_TENANT);
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        // A session must never outlive a change of tenant.
        return true;
    }
}
