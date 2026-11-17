package com.platform.tenants.resolve;

import com.platform.tenants.config.PlatformProperties;
import com.platform.tenants.domain.Tenant;
import com.platform.tenants.domain.TenantRepository;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Vanity-URL to tenant resolution. Exact match only, in priority order:
 * explicit tenant code, exact domain, exact tenant name, then the first DNS label
 * of a host under the platform domain (matched against tenant name only).
 *
 * Only ACTIVE tenants resolve. Unknown, suspended and still-provisioning tenants all
 * look identical to the caller (empty), so the endpoint cannot be used to enumerate them.
 */
@Service
public class TenantResolver {

    private static final Pattern HOST =
            Pattern.compile("^[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?)*$");

    private final TenantRepository tenants;
    private final PlatformProperties props;

    public TenantResolver(TenantRepository tenants, PlatformProperties props) {
        this.tenants = tenants;
        this.props = props;
    }

    /** Lowercases, strips a port and a trailing dot; empty if the result is not a plausible hostname. */
    public static Optional<String> normalizeHost(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String host = raw.trim().toLowerCase(Locale.ROOT);
        int colon = host.lastIndexOf(':');
        if (colon >= 0) {
            host = host.substring(0, colon);
        }
        if (host.endsWith(".")) {
            host = host.substring(0, host.length() - 1);
        }
        return host.length() <= 253 && HOST.matcher(host).matches() ? Optional.of(host) : Optional.empty();
    }

    public Optional<Tenant> resolve(String rawHost) {
        Optional<String> normalized = normalizeHost(rawHost);
        if (normalized.isEmpty()) {
            return Optional.empty();
        }
        String host = normalized.get();

        Optional<Tenant> found = tenants.findByCode(host);
        if (found.isEmpty()) {
            found = tenants.findByDomain(host);
        }
        if (found.isEmpty()) {
            found = tenants.findByName(host);
        }
        if (found.isEmpty()) {
            found = firstLabelUnderPlatformDomain(host).flatMap(tenants::findByName);
        }
        return found.filter(Tenant::isActive);
    }

    /** acme.example.com -> acme, but only when the host really is under the platform domain. */
    private Optional<String> firstLabelUnderPlatformDomain(String host) {
        String suffix = "." + props.domain().toLowerCase(Locale.ROOT);
        if (!host.endsWith(suffix)) {
            return Optional.empty();
        }
        String label = host.substring(0, host.length() - suffix.length());
        return label.contains(".") || label.isEmpty() ? Optional.empty() : Optional.of(label);
    }

    public TenantConfig configFor(Tenant tenant) {
        String realmBase = props.authServerUrl() + "/realms/" + tenant.realmName();
        String oidc = realmBase + "/protocol/openid-connect";
        return new TenantConfig(props.authServerUrl(), tenant.realmName(), tenant.realmName(),
                realmBase, oidc + "/certs", oidc + "/token");
    }
}
