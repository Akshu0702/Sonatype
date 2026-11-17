package com.platform.tenants;

import com.platform.tenants.config.PlatformProperties;
import com.platform.tenants.domain.Tenant;
import com.platform.tenants.domain.TenantRepository;
import com.platform.tenants.keycloak.KeycloakAdmin;
import com.platform.tenants.keycloak.KeycloakException;
import com.platform.tenants.provisioning.ProvisioningService;
import com.platform.tenants.provisioning.ProvisioningService.ProvisioningResult;
import com.platform.tenants.provisioning.ProvisioningStep;
import com.platform.tenants.resolve.DbTrustedIssuerRegistry;
import com.platform.tenants.resolve.TenantResolver;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Real Postgres, in-memory fake Keycloak. Runs when TEST_PG_URL / TEST_PG_ADMIN_USER / TEST_PG_ADMIN_PASSWORD are set.
 */
@EnabledIfEnvironmentVariable(named = "TEST_PG_URL", matches = ".+")
class ProvisioningPostgresTest {

    private static final PlatformProperties PROPS = new PlatformProperties("example.com", "https://auth.example.com",
            "http://keycloak:8180", "platform-automation", "x", List.of("platform-automation"), "platform-console", "platform-operator");

    private static JdbcClient jdbc;
    private static TenantRepository repo;

    private final FakeKeycloak keycloak = new FakeKeycloak();
    private final ProvisioningService provisioning = new ProvisioningService(repo, keycloak, PROPS);

    @BeforeAll
    static void migrate() {
        DriverManagerDataSource ds = new DriverManagerDataSource(System.getenv("TEST_PG_URL"),
                System.getenv("TEST_PG_ADMIN_USER"), System.getenv("TEST_PG_ADMIN_PASSWORD"));
        Flyway.configure().dataSource(ds).cleanDisabled(false).load().clean();
        Flyway.configure().dataSource(ds).load().migrate();
        jdbc = JdbcClient.create(ds);
        repo = new TenantRepository(jdbc);
    }

    @BeforeEach
    void clean() {
        jdbc.sql("DELETE FROM tenants").update();
    }

    private Tenant newTenant(String name) {
        return repo.insert(name, name.toUpperCase(), null, null, "admin@" + name + ".test", "ENTERPRISE", null, "NONE");
    }

    @Test
    void happyPathCreatesRealmClientAndAdminThenActivates() {
        Tenant t = newTenant("acme");

        ProvisioningResult result = provisioning.provision(t.tenantId());

        assertThat(result.error()).isNull();
        assertThat(result.tenant().status()).isEqualTo("active");
        assertThat(result.tenant().provisioningStepsCompleted()).isEqualTo(ProvisioningStep.ALL);
        assertThat(result.temporaryPassword()).isNotBlank();
        assertThat(keycloak.realms).containsExactly("acme");
        assertThat(keycloak.clients).containsExactly("acme/acme");
        assertThat(keycloak.users).containsExactly("acme/admin@acme.test");
    }

    @Test
    void aFailedRunKeepsCompletedStepsAndRetryResumesWithoutRecreatingThem() {
        Tenant t = newTenant("globex");
        keycloak.failOn = "createPublicClient";

        ProvisioningResult first = provisioning.provision(t.tenantId());

        assertThat(first.error()).isNotNull();
        assertThat(first.tenant().status()).isEqualTo("provisioning");
        assertThat(first.tenant().provisioningStepsCompleted()).isEqualTo(ProvisioningStep.REALM_CREATED.bit());

        keycloak.failOn = null;
        keycloak.calls.clear();
        ProvisioningResult second = provisioning.provision(t.tenantId());

        assertThat(second.tenant().status()).isEqualTo("active");
        assertThat(keycloak.calls).doesNotContain("createRealm");
        assertThat(keycloak.calls).contains("createPublicClient", "createUser");
    }

    @Test
    void aStepWhoseKeycloakObjectAlreadyExistsIsAdoptedNotRecreated() {
        Tenant t = newTenant("initech");
        keycloak.realms.add("initech"); // created by an earlier run that died before setting its bit

        ProvisioningResult result = provisioning.provision(t.tenantId());

        assertThat(result.tenant().status()).isEqualTo("active");
        assertThat(keycloak.calls).doesNotContain("createRealm");
    }

    @Test
    void anActiveTenantIsNotProvisionedAgain() {
        Tenant t = newTenant("hooli");
        provisioning.provision(t.tenantId());
        keycloak.calls.clear();

        ProvisioningResult again = provisioning.provision(t.tenantId());

        assertThat(again.notice()).isNotNull();
        assertThat(keycloak.calls).isEmpty();
    }

    @Test
    void aHeldLeaseStopsConcurrentProvisioning() {
        Tenant t = newTenant("umbrella");
        assertThat(repo.acquireLease(t.tenantId(), 300)).isTrue();

        ProvisioningResult result = provisioning.provision(t.tenantId());

        assertThat(result.notice()).contains("in progress");
        assertThat(keycloak.calls).isEmpty();
    }

    @Test
    void exactMatchLookupsDoNotLeakAcrossTenants() {
        newTenant("reachmotivity");
        Tenant motivity = newTenant("motivity");

        assertThat(repo.findByName("motivity")).map(Tenant::tenantId).contains(motivity.tenantId());
        assertThat(repo.findByName("motiv")).isEmpty();
        assertThat(repo.findByName("%")).isEmpty();
        assertThat(repo.findByName("motivity%")).isEmpty();
        assertThat(repo.findByDomain("%")).isEmpty();
    }

    @Test
    void duplicateNamesAreRejectedByTheDatabase() {
        newTenant("dup");
        assertThatThrownBy(() -> newTenant("dup")).isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void issuerRegistryTrustsOnlyActiveTenantsExactIssuer() {
        Tenant t = newTenant("wayne");
        DbTrustedIssuerRegistry registry = new DbTrustedIssuerRegistry(repo, PROPS);
        String issuer = "https://auth.example.com/realms/wayne";

        assertThat(registry.find(issuer)).isEmpty(); // still provisioning
        provisioning.provision(t.tenantId());

        assertThat(registry.find(issuer)).hasValueSatisfying(i -> {
            assertThat(i.tenantId()).isEqualTo(t.tenantId());
            assertThat(i.jwkSetUri()).isEqualTo("http://keycloak:8180/realms/wayne/protocol/openid-connect/certs");
        });
        assertThat(registry.find(issuer + "/")).isEmpty();
        assertThat(registry.find(issuer + "-corp")).isEmpty();
        assertThat(registry.find("https://auth.example.com/realms/master")).isEmpty();
        assertThat(registry.find("https://evil.example.org/realms/wayne")).isEmpty();
        assertThat(registry.find("https://auth.example.com/realms/wayne/x")).isEmpty();
    }

    @Test
    void resolverReturnsConfigPointingAtTheTenantsRealm() {
        Tenant t = newTenant("stark");
        provisioning.provision(t.tenantId());

        var config = new TenantResolver(repo, PROPS).resolve("stark.example.com")
                .map(found -> new TenantResolver(repo, PROPS).configFor(found)).orElseThrow();

        assertThat(config.realmName()).isEqualTo("stark");
        assertThat(config.issuerUri()).isEqualTo("https://auth.example.com/realms/stark");
        assertThat(config.tokenEndpoint()).isEqualTo("https://auth.example.com/realms/stark/protocol/openid-connect/token");
    }

    /** In-memory Keycloak that records calls and can be told to fail one operation. */
    static class FakeKeycloak implements KeycloakAdmin {
        final Set<String> realms = new HashSet<>();
        final Set<String> clients = new HashSet<>();
        final Set<String> users = new HashSet<>();
        final List<String> calls = new ArrayList<>();
        String failOn;

        private void record(String op) {
            calls.add(op);
            if (op.equals(failOn)) {
                throw new KeycloakException("injected failure in " + op);
            }
        }

        @Override public boolean realmExists(String realm) { return realms.contains(realm); }
        @Override public void createRealm(String realm, String displayName) { record("createRealm"); realms.add(realm); }
        @Override public boolean clientExists(String realm, String clientId) { return clients.contains(realm + "/" + clientId); }
        @Override public void createPublicClient(String realm, String clientId, List<String> uris) {
            record("createPublicClient");
            clients.add(realm + "/" + clientId);
        }
        final Set<String> disabledRealms = new HashSet<>();
        final java.util.Map<String, java.util.Map<String, String>> clientAttributes = new java.util.HashMap<>();

        final java.util.Map<String, String> passwords = new java.util.HashMap<>();

        @Override public void setClientAttributes(String realm, String clientId, java.util.Map<String, String> attributes) {
            record("setClientAttributes");
            java.util.Map<String, String> m = clientAttributes.computeIfAbsent(realm + "/" + clientId, k -> new java.util.HashMap<>());
            attributes.forEach((k, v) -> { if (v == null) m.remove(k); else m.put(k, v); });
        }
        final java.util.Map<String, String> displayNames = new java.util.HashMap<>();
        @Override public void setRealmDisplayName(String realm, String displayName) {
            record("setRealmDisplayName");
            displayNames.put(realm, displayName);
        }
        @Override public void setRealmEnabled(String realm, boolean enabled) {
            record(enabled ? "enableRealm" : "disableRealm");
            if (enabled) disabledRealms.remove(realm); else disabledRealms.add(realm);
        }
        @Override public boolean resetPassword(String realm, String username, String pw) {
            record("resetPassword");
            if (!users.contains(realm + "/" + username)) return false;
            passwords.put(realm + "/" + username, pw);
            return true;
        }
        @Override public boolean userExists(String realm, String username) { return users.contains(realm + "/" + username); }
        @Override public void createUser(String realm, String username, String email, String pw) {
            record("createUser");
            users.add(realm + "/" + username);
        }
    }
}
