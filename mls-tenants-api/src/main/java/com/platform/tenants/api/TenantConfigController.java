package com.platform.tenants.api;

import com.platform.security.jwt.TrustedIssuer;
import com.platform.tenants.resolve.DbTrustedIssuerRegistry;
import com.platform.tenants.resolve.TenantConfig;
import com.platform.tenants.resolve.TenantResolver;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.Optional;

@RestController
public class TenantConfigController {

    private final TenantResolver resolver;
    private final DbTrustedIssuerRegistry issuers;
    private final com.platform.tenants.domain.TenantRepository tenants;

    public TenantConfigController(TenantResolver resolver, DbTrustedIssuerRegistry issuers,
                                  com.platform.tenants.domain.TenantRepository tenants) {
        this.resolver = resolver;
        this.issuers = issuers;
        this.tenants = tenants;
    }

    /** Trusted internal callers (the gateway). Not exposed through the public edge. */
    @GetMapping("/tenant-config")
    public ResponseEntity<TenantConfig> internal(@RequestParam("host") String host) {
        return resolver.resolve(host).map(t -> ResponseEntity.ok(resolver.configFor(t)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** Browser-facing: the Referer host must equal the host query parameter (anti-spoofing). */
    @GetMapping("/tenant-config/v1")
    public ResponseEntity<TenantConfig> browser(@RequestParam("host") String host,
                                                @RequestHeader(value = "Referer", required = false) String referer) {
        Optional<String> wanted = TenantResolver.normalizeHost(host);
        Optional<String> refererHost = hostOf(referer);
        if (wanted.isEmpty() || refererHost.isEmpty() || !wanted.get().equals(refererHost.get())) {
            return ResponseEntity.notFound().build();
        }
        return internal(wanted.get());
    }

    /** What the gateway needs to route a host: the tenant id and its token issuer. Internal only. */
    public record ResolvedTenant(java.util.UUID tenantId, String realmName, String issuer) {
    }

    @GetMapping("/internal/resolve")
    public ResponseEntity<ResolvedTenant> resolveForGateway(@RequestParam("host") String host) {
        return resolver.resolve(host)
                .map(t -> ResponseEntity.ok(new ResolvedTenant(t.tenantId(), t.realmName(),
                        resolver.configFor(t).issuerUri())))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    public record TenantAdmin(String adminEmail) {
    }

    /** Lets IAM recognise the tenant's first administrator on first login. Internal only. */
    @GetMapping("/internal/tenants/{id}/admin")
    public ResponseEntity<TenantAdmin> admin(@org.springframework.web.bind.annotation.PathVariable("id") java.util.UUID id) {
        return tenants.findById(id).filter(com.platform.tenants.domain.Tenant::isActive)
                .map(t -> ResponseEntity.ok(new TenantAdmin(t.adminEmail())))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** Used by other services' issuer registries to learn which tenant owns an issuer. Internal only. */
    @GetMapping("/internal/trusted-issuer")
    public ResponseEntity<TrustedIssuer> trustedIssuer(@RequestParam("issuer") String issuer) {
        return issuers.find(issuer).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    private static Optional<String> hostOf(String referer) {
        if (referer == null) {
            return Optional.empty();
        }
        try {
            return TenantResolver.normalizeHost(URI.create(referer).getHost());
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
