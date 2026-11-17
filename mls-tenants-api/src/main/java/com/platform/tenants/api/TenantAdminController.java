package com.platform.tenants.api;

import com.platform.tenants.audit.AuditRecorder;
import com.platform.tenants.audit.Operator;
import com.platform.tenants.config.PlatformProperties;
import com.platform.tenants.domain.Tenant;
import com.platform.tenants.domain.TenantRepository;
import com.platform.tenants.provisioning.ProvisioningService;
import com.platform.tenants.provisioning.ProvisioningService.ProvisioningResult;
import com.platform.tenants.provisioning.TenantLifecycleService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Platform-operator API (master realm automation clients only; see SecurityConfig). */
@RestController
@RequestMapping("/api/tenants")
public class TenantAdminController {

    /** Names that would collide with platform hostnames or Keycloak's own realms. */
    static final Set<String> RESERVED = Set.of("master", "auth", "www", "api", "admin", "internal", "platform", "gateway");

    private final TenantRepository tenants;
    private final ProvisioningService provisioning;
    private final TenantLifecycleService lifecycle;
    private final AuditRecorder audit;
    private final PlatformProperties props;

    public TenantAdminController(TenantRepository tenants, ProvisioningService provisioning, TenantLifecycleService lifecycle,
                                 AuditRecorder audit, PlatformProperties props) {
        this.tenants = tenants;
        this.provisioning = provisioning;
        this.lifecycle = lifecycle;
        this.audit = audit;
        this.props = props;
    }

    public record CreateTenantRequest(
            @NotBlank @Pattern(regexp = "^[a-z0-9][a-z0-9-]{1,28}[a-z0-9]$") String name,
            @NotBlank @Size(max = 120) String displayName,
            @NotBlank @Email String adminEmail,
            @Pattern(regexp = "^[a-z0-9-]{3,40}$") String tenantCode,
            @Pattern(regexp = "^[a-z0-9]([a-z0-9.-]{0,251}[a-z0-9])?$") String domain,
            @Pattern(regexp = "MASTER_MSSP|MSSP|ENTERPRISE") String tenantType,
            UUID parentTenantId,
            @Pattern(regexp = "NONE|READ_ONLY|IMPERSONATE") String parentAccessLevel) {
    }

    public record TenantView(UUID tenantId, String name, String displayName, String status, String tenantType,
                             UUID parentTenantId, int provisioningStepsCompleted, String temporaryAdminPassword,
                             String message, String adminEmail, String domain, Instant createdAt, String signInUrl) {
        static TenantView of(Tenant t, String password, String message, String platformDomain) {
            return new TenantView(t.tenantId(), t.tenantName(), t.displayName(), t.status(), t.tenantType(),
                    t.parentTenantId(), t.provisioningStepsCompleted(), password, message, t.adminEmail(), t.domain(), t.createdAt(),
                    "https://" + t.tenantName() + "." + platformDomain + "/");
        }
    }

    @PostMapping
    public ResponseEntity<TenantView> create(@Valid @RequestBody CreateTenantRequest req, @AuthenticationPrincipal Jwt jwt) {
        String name = req.name().toLowerCase(Locale.ROOT);
        if (RESERVED.contains(name)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Reserved tenant name");
        }
        if (req.parentTenantId() != null && tenants.findById(req.parentTenantId()).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown parent tenant");
        }
        Tenant created;
        try {
            created = tenants.insert(name, req.displayName(), req.tenantCode(), req.domain(),
                    req.adminEmail().toLowerCase(Locale.ROOT),
                    req.tenantType() == null ? "ENTERPRISE" : req.tenantType(), req.parentTenantId(),
                    req.parentAccessLevel() == null ? "NONE" : req.parentAccessLevel());
        } catch (DuplicateKeyException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Name, code or domain already in use");
        }
        audit.record(Operator.of(jwt), created.tenantId(), "tenant.created", Map.of("name", name, "adminEmail", created.adminEmail(), "type", created.tenantType()));
        return respond(provisioning.provision(created.tenantId()), HttpStatus.CREATED);
    }

    @GetMapping
    public List<TenantView> list() {
        return tenants.findAll().stream().map(t -> TenantView.of(t, null, null, props.domain())).toList();
    }

    @PostMapping("/{id}/provision")
    public ResponseEntity<TenantView> retry(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        if (tenants.findById(id).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        audit.record(Operator.of(jwt), id, "tenant.provision.retried", Map.of());
        return respond(provisioning.provision(id), HttpStatus.OK);
    }

    @GetMapping("/{id}")
    public TenantView get(@PathVariable UUID id) {
        return tenants.findById(id).map(t -> TenantView.of(t, null, null, props.domain()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    public record Reason(@Size(max = 200) String reason) {
    }

    public record Profile(@Size(max = 120) String displayName, @Size(max = 253) String domain, @Size(max = 40) String tenantCode) {
    }

    public record Offboard(@NotBlank String confirmName, @Size(max = 200) String reason) {
    }

    @PostMapping("/{id}/suspend")
    public TenantView suspend(@PathVariable UUID id, @RequestBody(required = false) Reason body, @AuthenticationPrincipal Jwt jwt) {
        return TenantView.of(lifecycle.suspend(id, body == null ? null : body.reason(), Operator.of(jwt)), null, null, props.domain());
    }

    @PatchMapping("/{id}")
    public TenantView update(@PathVariable UUID id, @Valid @RequestBody Profile body, @AuthenticationPrincipal Jwt jwt) {
        return TenantView.of(lifecycle.update(id, body.displayName(), body.domain(), body.tenantCode(), Operator.of(jwt)), null, null, props.domain());
    }

    @PostMapping("/{id}/offboard")
    public TenantView offboard(@PathVariable UUID id, @Valid @RequestBody Offboard body, @AuthenticationPrincipal Jwt jwt) {
        return TenantView.of(lifecycle.offboard(id, body.confirmName(), body.reason(), Operator.of(jwt)), null, null, props.domain());
    }

    /** Counts of what the tenant has, from the services that own it. A section is null when its service did not answer. */

    /** What the platform itself did to this tenant (never the tenant's own activity). */

    @PostMapping("/{id}/activate")
    public TenantView activate(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return TenantView.of(lifecycle.activate(id, Operator.of(jwt)), null, null, props.domain());
    }

    /** The new temporary password is in the answer and nowhere else. */
    @PostMapping("/{id}/reset-admin-password")
    public TenantView resetAdminPassword(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        String password = lifecycle.resetAdminPassword(id, Operator.of(jwt));
        return TenantView.of(tenants.findById(id).orElseThrow(), password, null, props.domain());
    }

    private ResponseEntity<TenantView> respond(ProvisioningResult r, HttpStatus success) {
        if (r.error() != null) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(TenantView.of(r.tenant(), null, r.error(), props.domain()));
        }
        if (r.notice() != null) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(TenantView.of(r.tenant(), null, r.notice(), props.domain()));
        }
        return ResponseEntity.status(success).body(TenantView.of(r.tenant(), r.temporaryPassword(), null, props.domain()));
    }
}
