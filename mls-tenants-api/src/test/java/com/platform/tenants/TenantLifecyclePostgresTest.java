package com.platform.tenants;

import com.platform.tenants.ProvisioningPostgresTest.FakeKeycloak;
import com.platform.tenants.audit.AuditRecorder;
import com.platform.tenants.audit.Operator;
import com.platform.tenants.config.PlatformProperties;
import com.platform.tenants.domain.Tenant;
import com.platform.tenants.domain.TenantRepository;
import com.platform.tenants.provisioning.ProvisioningService;
import com.platform.tenants.provisioning.TenantLifecycleService;
import com.platform.tenants.resolve.DbTrustedIssuerRegistry;
import com.platform.tenants.resolve.TenantResolver;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Real Postgres, in-memory fake Keycloak: what an operator can do to a tenant that exists. */
@EnabledIfEnvironmentVariable(named = "TEST_PG_URL", matches = ".+")
class TenantLifecyclePostgresTest {

    private static final PlatformProperties PROPS = new PlatformProperties("example.com", "https://auth.example.com",
            "http://keycloak:8180", "platform-automation", "x", List.of("platform-automation"), "platform-console", "platform-operator");
    private static final Operator OPERATOR = new Operator(UUID.randomUUID(), "ops@platform.test");

    private static JdbcClient jdbc;
    private static TenantRepository repo;
    private static TenantLifecycleService lifecycle;
    private static FakeKeycloak keycloak;
    private static ProvisioningService provisioning;

    @BeforeAll
    static void migrate() {
        DriverManagerDataSource ds = new DriverManagerDataSource(System.getenv("TEST_PG_URL"),
                System.getenv("TEST_PG_ADMIN_USER"), System.getenv("TEST_PG_ADMIN_PASSWORD"));
        Flyway.configure().dataSource(ds).cleanDisabled(false).load().clean();
        Flyway.configure().dataSource(ds).load().migrate();
        jdbc = JdbcClient.create(ds);
        repo = new TenantRepository(jdbc);
        keycloak = new FakeKeycloak();
        lifecycle = new TenantLifecycleService(repo, keycloak,
                new AuditRecorder(jdbc, tools.jackson.databind.json.JsonMapper.builder().build()), new TransactionTemplate(new DataSourceTransactionManager(ds)));
        provisioning = new ProvisioningService(repo, keycloak, PROPS);
    }

    @BeforeEach
    void clean() {
        jdbc.sql("DELETE FROM audit_outbox").update();
        jdbc.sql("DELETE FROM tenants").update();
        keycloak.realms.clear();
        keycloak.users.clear();
        keycloak.clients.clear();
        keycloak.disabledRealms.clear();
        keycloak.passwords.clear();
        keycloak.calls.clear();
        keycloak.failOn = null;
    }

    private Tenant active(String name) {
        Tenant t = repo.insert(name, name.toUpperCase(), null, null, "admin@" + name + ".test", "ENTERPRISE", null, "NONE");
        assertThat(provisioning.provision(t.tenantId()).tenant().status()).isEqualTo("active");
        keycloak.calls.clear();
        return repo.findById(t.tenantId()).orElseThrow();
    }

    private List<String> auditActions(UUID tenant) {
        return jdbc.sql("SELECT payload->>'action' FROM audit_outbox WHERE tenant_id = :t ORDER BY id").param("t", tenant).query(String.class).list();
    }

    @Test
    void suspendingStopsSignInAndResolutionAndIsAudited() {
        Tenant t = active("wayne");
        TenantResolver resolver = new TenantResolver(repo, PROPS);
        DbTrustedIssuerRegistry registry = new DbTrustedIssuerRegistry(repo, PROPS);
        assertThat(resolver.resolve("wayne.example.com")).isPresent();
        assertThat(registry.find("https://auth.example.com/realms/wayne")).isPresent();

        Tenant suspended = lifecycle.suspend(t.tenantId(), null, OPERATOR);

        assertThat(suspended.status()).isEqualTo("suspended");
        assertThat(keycloak.disabledRealms).contains("wayne");
        assertThat(resolver.resolve("wayne.example.com")).isEmpty();                       // the sign-in page no longer finds it
        assertThat(registry.find("https://auth.example.com/realms/wayne")).isEmpty();      // and no service trusts its tokens
        assertThat(auditActions(t.tenantId())).containsExactly("tenant.suspended");
        String payload = jdbc.sql("SELECT payload::text FROM audit_outbox WHERE tenant_id = :t").param("t", t.tenantId()).query(String.class).single();
        assertThat(payload).contains("ops@platform.test").contains(OPERATOR.id().toString());
    }

    @Test
    void reactivatingRestoresEverythingAndRepeatingACallIsHarmless() {
        Tenant t = active("stark");
        lifecycle.suspend(t.tenantId(), null, OPERATOR);
        lifecycle.suspend(t.tenantId(), null, OPERATOR);                                         // a retry after a half-finished attempt
        assertThat(repo.findById(t.tenantId()).orElseThrow().status()).isEqualTo("suspended");

        Tenant back = lifecycle.activate(t.tenantId(), OPERATOR);

        assertThat(back.status()).isEqualTo("active");
        assertThat(keycloak.disabledRealms).doesNotContain("stark");
        assertThat(new TenantResolver(repo, PROPS).resolve("stark.example.com")).isPresent();
        assertThat(auditActions(t.tenantId())).containsExactly("tenant.suspended", "tenant.suspended", "tenant.activated");
    }

    @Test
    void ifKeycloakFailsNothingChangesAndNothingIsClaimed() {
        Tenant t = active("oscorp");
        keycloak.failOn = "disableRealm";

        assertThatThrownBy(() -> lifecycle.suspend(t.tenantId(), null, OPERATOR)).isInstanceOf(RuntimeException.class);

        assertThat(repo.findById(t.tenantId()).orElseThrow().status()).isEqualTo("active");
        assertThat(auditActions(t.tenantId())).isEmpty();
    }

    @Test
    void onlyTenantsInTheRightStateCanBeSuspendedOrReactivated() {
        Tenant pending = repo.insert("umbrella", "Umbrella", null, null, "a@umbrella.test", "ENTERPRISE", null, "NONE");
        assertThatThrownBy(() -> lifecycle.suspend(pending.tenantId(), null, OPERATOR)).isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode().value()).isEqualTo(409));
        assertThatThrownBy(() -> lifecycle.activate(pending.tenantId(), OPERATOR)).isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode().value()).isEqualTo(409));
        assertThatThrownBy(() -> lifecycle.suspend(UUID.randomUUID(), null, OPERATOR)).isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode().value()).isEqualTo(404));
        assertThat(keycloak.calls).isEmpty();
    }

    @Test
    void resettingTheAdminPasswordGivesANewOneOnceAndNeverRecordsIt() {
        Tenant t = active("hooli");
        String first = lifecycle.resetAdminPassword(t.tenantId(), OPERATOR);
        String second = lifecycle.resetAdminPassword(t.tenantId(), OPERATOR);

        assertThat(first).hasSizeGreaterThanOrEqualTo(20).isNotEqualTo(second);
        assertThat(keycloak.passwords.get("hooli/admin@hooli.test")).isEqualTo(second);
        String all = String.join("\n", jdbc.sql("SELECT payload::text FROM audit_outbox").query(String.class).list());
        assertThat(all).contains("tenant.admin_password.reset").doesNotContain(first).doesNotContain(second);
    }

    @Test
    void thePasswordCanOnlyBeResetForAnActiveTenantWhoseAdminExists() {
        Tenant t = active("pied");
        lifecycle.suspend(t.tenantId(), null, OPERATOR);
        assertThatThrownBy(() -> lifecycle.resetAdminPassword(t.tenantId(), OPERATOR)).isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode().value()).isEqualTo(409));

        lifecycle.activate(t.tenantId(), OPERATOR);
        keycloak.users.clear();                                                           // the admin was removed inside the realm
        assertThatThrownBy(() -> lifecycle.resetAdminPassword(t.tenantId(), OPERATOR)).isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode().value()).isEqualTo(404));
    }

    @Test
    void aSuspensionCanCarryAReasonThatIsAuditedButNotRequired() {
        Tenant t = active("reason");
        lifecycle.suspend(t.tenantId(), "Unpaid invoice 2026-10", OPERATOR);
        String payload = jdbc.sql("SELECT payload::text FROM audit_outbox WHERE tenant_id = :t").param("t", t.tenantId()).query(String.class).single();
        assertThat(payload).contains("Unpaid invoice 2026-10");
    }

    @Test
    void theProfileCanBeEditedAndTheSignInPagesFollowTheDisplayName() {
        Tenant t = active("edit");
        Tenant changed = lifecycle.update(t.tenantId(), "  Edit Industries ", "Edit.Example.COM", "EDIT-001", OPERATOR);

        assertThat(changed.displayName()).isEqualTo("Edit Industries");
        assertThat(changed.domain()).isEqualTo("edit.example.com");
        assertThat(changed.tenantCode()).isEqualTo("edit-001");
        assertThat(keycloak.displayNames.get("edit")).isEqualTo("Edit Industries");
        assertThat(auditActions(t.tenantId())).contains("tenant.updated");

        // a blank field leaves that field alone
        Tenant again = lifecycle.update(t.tenantId(), "Edit Two", "", "", OPERATOR);
        assertThat(again.domain()).isEqualTo("edit.example.com");
        assertThat(again.displayName()).isEqualTo("Edit Two");
    }

    @Test
    void profileEditsAreValidatedAndDomainsStayUnique() {
        Tenant a = active("alpha");
        Tenant b = active("bravo");
        lifecycle.update(a.tenantId(), null, "shared.example.com", null, OPERATOR);

        assertThatThrownBy(() -> lifecycle.update(b.tenantId(), null, "shared.example.com", null, OPERATOR)).isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode().value()).isEqualTo(409));
        for (String[] bad : new String[][]{{null, "not a domain", null}, {null, null, "x"}, {"", "", ""}, {"y".repeat(121), null, null}}) {
            assertThatThrownBy(() -> lifecycle.update(b.tenantId(), bad[0], bad[1], bad[2], OPERATOR)).isInstanceOfSatisfying(ResponseStatusException.class,
                    e -> assertThat(e.getStatusCode().value()).isEqualTo(400));
        }
        assertThat(repo.findById(b.tenantId()).orElseThrow().domain()).isNull();
    }

    @Test
    void offboardingNeedsTheNameEndsServiceAndHasNoWayBack() {
        Tenant t = active("gone");
        assertThatThrownBy(() -> lifecycle.offboard(t.tenantId(), "wrong", "x", OPERATOR)).isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode().value()).isEqualTo(400));
        assertThat(repo.findById(t.tenantId()).orElseThrow().status()).isEqualTo("active");

        Tenant off = lifecycle.offboard(t.tenantId(), "gone", "Contract ended", OPERATOR);
        assertThat(off.status()).isEqualTo("offboarded");
        assertThat(keycloak.disabledRealms).contains("gone");
        assertThat(new TenantResolver(repo, PROPS).resolve("gone.example.com")).isEmpty();
        assertThat(auditActions(t.tenantId())).contains("tenant.offboarded");

        for (Runnable r : List.<Runnable>of(() -> lifecycle.activate(t.tenantId(), OPERATOR), () -> lifecycle.suspend(t.tenantId(), null, OPERATOR),
                () -> lifecycle.offboard(t.tenantId(), "gone", null, OPERATOR), () -> lifecycle.update(t.tenantId(), "New", null, null, OPERATOR),
                () -> lifecycle.resetAdminPassword(t.tenantId(), OPERATOR))) {
            assertThatThrownBy(r::run).isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode().value()).isEqualTo(409));
        }
    }

    @Test
    void aTenantStillBeingSetUpCannotBeOffboarded() {
        Tenant pending = repo.insert("midway", "Midway", null, null, "a@midway.test", "ENTERPRISE", null, "NONE");
        assertThatThrownBy(() -> lifecycle.offboard(pending.tenantId(), "midway", null, OPERATOR)).isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode().value()).isEqualTo(409));
    }

    @Test
    void tenantsAreListedNewestFirst() throws Exception {
        Tenant a = active("first");
        Thread.sleep(15);
        Tenant b = active("second");

        assertThat(repo.findAll()).extracting(Tenant::tenantName).containsExactly("second", "first");
        assertThat(a.tenantId()).isNotEqualTo(b.tenantId());
    }
}
