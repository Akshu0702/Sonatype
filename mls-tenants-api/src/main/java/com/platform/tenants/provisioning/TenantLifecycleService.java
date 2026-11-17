package com.platform.tenants.provisioning;

import com.platform.tenants.audit.AuditRecorder;
import com.platform.tenants.audit.Operator;
import com.platform.tenants.domain.Tenant;
import com.platform.tenants.domain.TenantRepository;
import com.platform.tenants.keycloak.KeycloakAdmin;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * What an operator can do to a tenant that already exists. Suspending turns off both the tenant's sign-in (its Keycloak
 * realm) and its place in the registry (so no host or token resolves to it). Every step is safe to repeat, so a call that
 * failed half way is fixed by calling it again: Keycloak is changed first, then the registry and the audit record
 * together in one transaction.
 */
@Service
public class TenantLifecycleService {

    private final TenantRepository tenants;
    private final KeycloakAdmin keycloak;
    private final AuditRecorder audit;
    private final TransactionTemplate tx;
    private final SecureRandom random = new SecureRandom();

    public TenantLifecycleService(TenantRepository tenants, KeycloakAdmin keycloak, AuditRecorder audit, TransactionTemplate tx) {
        this.tenants = tenants;
        this.keycloak = keycloak;
        this.audit = audit;
        this.tx = tx;
    }

    public Tenant suspend(UUID id, String reason, Operator by) {
        Tenant t = find(id);
        if (!List.of("active", "suspended").contains(t.status())) {
            throw conflict("Only an active tenant can be suspended");
        }
        keycloak.setRealmEnabled(t.realmName(), false);
        tx.executeWithoutResult(s -> {
            tenants.transition(id, List.of("active", "suspended"), "suspended");
            audit.record(by, id, "tenant.suspended", withReason(Map.of("name", t.tenantName()), reason));
        });
        return find(id);
    }

    /** Display name, the customer's own domain and the tenant code. A blank value leaves a field as it is. */
    public Tenant update(UUID id, String displayName, String domain, String code, Operator by) {
        Tenant t = find(id);
        if ("offboarded".equals(t.status())) {
            throw conflict("An offboarded tenant cannot be changed");
        }
        String name = blankToNull(displayName);
        if (name != null && name.length() > 120) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The display name can be at most 120 characters");
        }
        String d = blankToNull(domain) == null ? null : domain.trim().toLowerCase(java.util.Locale.ROOT);
        String c = blankToNull(code) == null ? null : code.trim().toLowerCase(java.util.Locale.ROOT);
        if (d != null && !d.matches("^[a-z0-9]([a-z0-9.-]{0,251}[a-z0-9])?$")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The domain must look like example.com");
        }
        if (c != null && !c.matches("^[a-z0-9-]{3,40}$")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The tenant code is 3-40 lowercase letters, digits or dashes");
        }
        if (name == null && d == null && c == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nothing to change");
        }
        if (name != null) {
            keycloak.setRealmDisplayName(t.realmName(), name);
        }
        try {
            tx.executeWithoutResult(s -> {
                tenants.updateProfile(id, name, d, c);
                Map<String, Object> changed = new java.util.LinkedHashMap<>();
                if (name != null) changed.put("displayName", name);
                if (d != null) changed.put("domain", d);
                if (c != null) changed.put("tenantCode", c);
                audit.record(by, id, "tenant.updated", changed);
            });
        } catch (org.springframework.dao.DuplicateKeyException e) {
            throw conflict("That domain or tenant code is already used by another tenant");
        }
        return find(id);
    }

    /**
     * Ends the tenant's service: sign-in is switched off and it leaves the registry, like a suspension, but there is no way back
     * through the console. Nothing is deleted. The operator must type the tenant's name, so it is never one stray click.
     */
    public Tenant offboard(UUID id, String confirmName, String reason, Operator by) {
        Tenant t = find(id);
        if (!t.tenantName().equals(confirmName == null ? "" : confirmName.trim())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Type the tenant's name to confirm");
        }
        if (!List.of("active", "suspended").contains(t.status())) {
            throw conflict("Only an active or suspended tenant can be offboarded");
        }
        keycloak.setRealmEnabled(t.realmName(), false);
        tx.executeWithoutResult(s -> {
            tenants.transition(id, List.of("active", "suspended"), "offboarded");
            audit.record(by, id, "tenant.offboarded", withReason(Map.of("name", t.tenantName()), reason));
        });
        return find(id);
    }

    private static Map<String, Object> withReason(Map<String, Object> base, String reason) {
        String r = blankToNull(reason);
        if (r == null) {
            return base;
        }
        Map<String, Object> out = new java.util.LinkedHashMap<>(base);
        out.put("reason", r.length() > 200 ? r.substring(0, 200) : r);
        return out;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    public Tenant activate(UUID id, Operator by) {
        Tenant t = find(id);
        if (!List.of("active", "suspended").contains(t.status())) {
            throw conflict("Only a suspended tenant can be reactivated");
        }
        keycloak.setRealmEnabled(t.realmName(), true);
        tx.executeWithoutResult(s -> {
            tenants.transition(id, List.of("active", "suspended"), "active");
            audit.record(by, id, "tenant.activated", Map.of("name", t.tenantName()));
        });
        return find(id);
    }

    /** A new temporary password for the tenant's first administrator, who must change it at the next sign-in. Shown once. */
    public String resetAdminPassword(UUID id, Operator by) {
        Tenant t = find(id);
        if (!"active".equals(t.status())) {
            throw conflict("The password can only be reset for an active tenant");
        }
        String password = newTemporaryPassword();
        if (!keycloak.resetPassword(t.realmName(), t.adminEmail(), password)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "The administrator account does not exist in the tenant's realm");
        }
        tx.executeWithoutResult(s -> audit.record(by, id, "tenant.admin_password.reset", Map.of("adminEmail", t.adminEmail())));
        return password;
    }

    private Tenant find(UUID id) {
        return tenants.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    private static ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }

    private String newTemporaryPassword() {
        byte[] bytes = new byte[18];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
