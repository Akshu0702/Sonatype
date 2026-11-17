package com.platform.tenants.resolve;

import com.platform.security.jwt.TrustedIssuer;
import com.platform.security.jwt.TrustedIssuerRegistry;
import com.platform.tenants.config.PlatformProperties;
import com.platform.tenants.domain.Tenant;
import com.platform.tenants.domain.TenantRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * The platform's source of truth for "which token issuers do we trust, and which tenant does each belong to".
 * An issuer is trusted only if it is exactly {authServerUrl}/realms/{realm} for an ACTIVE tenant's realm.
 */
@Component
public class DbTrustedIssuerRegistry implements TrustedIssuerRegistry {

    private final TenantRepository tenants;
    private final PlatformProperties props;

    public DbTrustedIssuerRegistry(TenantRepository tenants, PlatformProperties props) {
        this.tenants = tenants;
        this.props = props;
    }

    @Override
    public Optional<TrustedIssuer> find(String issuer) {
        String prefix = props.authServerUrl() + "/realms/";
        if (issuer == null || !issuer.startsWith(prefix)) {
            return Optional.empty();
        }
        String realm = issuer.substring(prefix.length());
        if (realm.isEmpty() || realm.contains("/")) {
            return Optional.empty();
        }
        return tenants.findByRealm(realm)
                .filter(Tenant::isActive)
                .map(t -> new TrustedIssuer(
                        prefix + t.realmName(),
                        props.keycloakInternalUrl() + "/realms/" + t.realmName() + "/protocol/openid-connect/certs",
                        t.tenantId()));
    }
}
