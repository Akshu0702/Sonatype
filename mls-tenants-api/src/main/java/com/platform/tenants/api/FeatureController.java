package com.platform.tenants.api;

import com.platform.tenants.audit.Operator;
import com.platform.tenants.audit.PlatformAudit;
import com.platform.tenants.features.EntitlementService;
import com.platform.tenants.features.EntitlementService.Change;
import com.platform.tenants.features.EntitlementService.State;
import com.platform.tenants.features.EntitlementService.TenantEntitlements;
import com.platform.tenants.features.PackageService;
import com.platform.tenants.features.PackageService.Pkg;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Packages and what each tenant may use. The operator's side is under {@code /api} (master-realm operators only, like the rest of the tenant API).
 * The internal side is for the other services on the container network: they read the live set, and IAM relays a tenant administrator's switch
 * after checking their permission.
 */
@RestController
public class FeatureController {

    public record OverrideBody(@NotNull Boolean on, @NotBlank @Size(max = 300) String reason, LocalDate until) {
    }

    public record LimitBody(Long value, @NotBlank @Size(max = 300) String reason) {
    }

    public record PreviewBody(@NotBlank String pkg) {
    }

    public record PackageBody(@NotBlank String pkg) {
    }

    public record Enable(@NotNull Boolean enabled) {
    }

    private final PackageService packages;
    private final EntitlementService entitlements;
    private final PlatformAudit platformAudit;

    public FeatureController(PackageService packages, EntitlementService entitlements, PlatformAudit platformAudit) {
        this.packages = packages;
        this.entitlements = entitlements;
        this.platformAudit = platformAudit;
    }

    // ---- packages

    @GetMapping("/api/catalogue")
    public List<EntitlementService.CatalogueProduct> catalogue() {
        return entitlements.catalogue();
    }

    @GetMapping("/api/packages")
    public List<Pkg> packages() {
        return packages.list();
    }

    @GetMapping("/api/packages/{key}/{version}")
    public Pkg pkg(@PathVariable String key, @PathVariable int version) {
        return packages.get(key, version);
    }

    @PostMapping("/api/packages/{key}/versions")
    public Pkg newVersion(@PathVariable String key, @AuthenticationPrincipal Jwt jwt) {
        return packages.newVersion(key, Operator.of(jwt));
    }

    @PutMapping("/api/packages/{key}/{version}")
    public Pkg update(@PathVariable String key, @PathVariable int version, @RequestBody PackageService.Update body, @AuthenticationPrincipal Jwt jwt) {
        return packages.update(key, version, body, Operator.of(jwt));
    }

    @PostMapping("/api/packages/{key}/{version}/publish")
    public Pkg publish(@PathVariable String key, @PathVariable int version, @AuthenticationPrincipal Jwt jwt) {
        return packages.publish(key, version, Operator.of(jwt));
    }

    // ---- what a tenant may use

    @GetMapping("/api/tenants/{id}/entitlements")
    public TenantEntitlements get(@PathVariable UUID id) {
        return entitlements.entitlements(id);
    }

    @PutMapping("/api/tenants/{id}/entitlements/{kind}/{key}")
    public TenantEntitlements override(@PathVariable UUID id, @PathVariable String kind, @PathVariable String key, @Valid @RequestBody OverrideBody body,
                                       @AuthenticationPrincipal Jwt jwt) {
        return entitlements.override(id, kindOf(kind), key, body.on(), body.reason(), body.until(), Operator.of(jwt));
    }

    @DeleteMapping("/api/tenants/{id}/entitlements/{kind}/{key}")
    public TenantEntitlements removeOverride(@PathVariable UUID id, @PathVariable String kind, @PathVariable String key, @AuthenticationPrincipal Jwt jwt) {
        return entitlements.removeOverride(id, kindOf(kind), key, Operator.of(jwt));
    }

    @PutMapping("/api/tenants/{id}/limits/{limitKey}")
    public TenantEntitlements limit(@PathVariable UUID id, @PathVariable String limitKey, @Valid @RequestBody LimitBody body, @AuthenticationPrincipal Jwt jwt) {
        return entitlements.overrideLimit(id, limitKey, body.value(), body.reason(), Operator.of(jwt));
    }

    @DeleteMapping("/api/tenants/{id}/limits/{limitKey}")
    public TenantEntitlements removeLimit(@PathVariable UUID id, @PathVariable String limitKey, @AuthenticationPrincipal Jwt jwt) {
        return entitlements.removeLimitOverride(id, limitKey, Operator.of(jwt));
    }

    @PostMapping("/api/tenants/{id}/entitlements/preview")
    public Change preview(@PathVariable UUID id, @Valid @RequestBody PreviewBody body) {
        return entitlements.preview(id, body.pkg());
    }

    @PutMapping("/api/tenants/{id}/package")
    public TenantEntitlements changePackage(@PathVariable UUID id, @Valid @RequestBody PackageBody body, @AuthenticationPrincipal Jwt jwt) {
        return entitlements.changePackage(id, body.pkg(), Operator.of(jwt));
    }

    // ---- the platform's own audit trail

    @GetMapping("/api/platform/audit")
    public List<PlatformAudit.Row> audit(@RequestParam(defaultValue = "100") int limit) {
        return platformAudit.list(limit);
    }

    @GetMapping("/api/platform/audit/chain")
    public PlatformAudit.Chain auditChain() {
        return platformAudit.verify();
    }

    // ---- internal (container network only; the gateway answers 404 for /internal)

    @GetMapping("/internal/catalogue")
    public EntitlementService.CatalogueKeys internalCatalogue() {
        return entitlements.catalogueKeys();
    }

    @GetMapping("/internal/tenants/{id}/features")
    public List<State> internalList(@PathVariable UUID id) {
        return entitlements.states(id);
    }

    @PutMapping("/internal/tenants/{id}/features/{key}/enabled")
    public List<State> switchOwn(@PathVariable UUID id, @PathVariable String key, @Valid @RequestBody Enable body, @RequestHeader("X-Actor-Id") UUID actor,
                                 @RequestHeader(value = "X-Actor-Name", defaultValue = "tenant administrator") String name) {
        return entitlements.setEnabled(id, key, body.enabled(), new Operator(actor, name));
    }

    private static String kindOf(String path) {
        return switch (path) {
            case "products" -> "product";
            case "features" -> "feature";
            case "feature-groups" -> "group";
            default -> throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown kind");
        };
    }
}
