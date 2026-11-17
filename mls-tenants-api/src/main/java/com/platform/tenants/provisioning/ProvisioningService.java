package com.platform.tenants.provisioning;

import com.platform.tenants.config.PlatformProperties;
import com.platform.tenants.domain.Tenant;
import com.platform.tenants.domain.TenantRepository;
import com.platform.tenants.keycloak.KeycloakAdmin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * Runs the tenant provisioning steps. Each step probes Keycloak first ("does it already exist?"),
 * performs the create only if missing, then sets its bit. A failed run leaves the bits of the
 * steps that succeeded, so a retry resumes where it stopped. A short lease stops two runs
 * (for example a double-click and a retry) from provisioning the same tenant at once.
 */
@Service
public class ProvisioningService {

    private static final Logger log = LoggerFactory.getLogger(ProvisioningService.class);
    private static final int LEASE_SECONDS = 300;

    private final TenantRepository tenants;
    private final KeycloakAdmin keycloak;
    private final PlatformProperties props;
    private final SecureRandom random = new SecureRandom();

    public ProvisioningService(TenantRepository tenants, KeycloakAdmin keycloak, PlatformProperties props) {
        this.tenants = tenants;
        this.keycloak = keycloak;
        this.props = props;
    }

    public ProvisioningResult provision(UUID tenantId) {
        Tenant tenant = tenants.findById(tenantId).orElseThrow(() -> new IllegalArgumentException("Unknown tenant"));
        if (!"provisioning".equals(tenant.status())) {
            return new ProvisioningResult(tenant, null, null, "Tenant is not in provisioning state");
        }
        if (!tenants.acquireLease(tenantId, LEASE_SECONDS)) {
            return new ProvisioningResult(tenant, null, null, "Provisioning is already in progress");
        }
        String temporaryPassword = null;
        try {
            for (ProvisioningStep step : ProvisioningStep.values()) {
                int done = tenants.findById(tenantId).orElseThrow().provisioningStepsCompleted();
                if (step.isDoneIn(done)) {
                    continue;
                }
                String generated = run(step, tenant);
                if (generated != null) {
                    temporaryPassword = generated;
                }
                tenants.markStepDone(tenantId, step.bit());
            }
            tenants.markActive(tenantId);
            return new ProvisioningResult(tenants.findById(tenantId).orElseThrow(), temporaryPassword, null, null);
        } catch (RuntimeException e) {
            // Log detail server-side only; the caller gets the failed step name, not Keycloak internals.
            log.error("Provisioning of tenant {} failed", tenantId, e);
            return new ProvisioningResult(tenants.findById(tenantId).orElseThrow(), temporaryPassword,
                    "Provisioning stopped; call retry to resume", null);
        } finally {
            tenants.releaseLease(tenantId);
        }
    }

    /** Returns a temporary password only when a user was created in this run. */
    private String run(ProvisioningStep step, Tenant tenant) {
        String realm = tenant.realmName();
        switch (step) {
            case REALM_CREATED -> {
                if (!keycloak.realmExists(realm)) {
                    keycloak.createRealm(realm, tenant.displayName());
                }
            }
            case CLIENT_CREATED -> {
                if (!keycloak.clientExists(realm, realm)) {
                    keycloak.createPublicClient(realm, realm, redirectUris(tenant));
                }
            }
            case ADMIN_USER_CREATED -> {
                String username = tenant.adminEmail();
                if (!keycloak.userExists(realm, username)) {
                    String password = newTemporaryPassword();
                    keycloak.createUser(realm, username, tenant.adminEmail(), password);
                    return password;
                }
            }
        }
        return null;
    }

    private List<String> redirectUris(Tenant tenant) {
        return List.of("https://" + tenant.tenantName() + "." + props.domain() + "/*");
    }

    private String newTemporaryPassword() {
        byte[] bytes = new byte[18];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public record ProvisioningResult(Tenant tenant, String temporaryPassword, String error, String notice) {
    }
}
