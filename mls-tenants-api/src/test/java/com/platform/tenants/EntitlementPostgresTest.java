package com.platform.tenants;

import com.platform.tenants.audit.AuditRecorder;
import com.platform.tenants.audit.Operator;
import com.platform.tenants.audit.PlatformAudit;
import com.platform.tenants.domain.Tenant;
import com.platform.tenants.domain.TenantRepository;
import com.platform.tenants.features.EntitlementService;
import com.platform.tenants.features.EntitlementService.GroupView;
import com.platform.tenants.features.EntitlementService.Resolved;
import com.platform.tenants.features.EntitlementService.TenantEntitlements;
import com.platform.tenants.features.PackageService;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Real Postgres: what a tenant may use, through its Products, package and overrides. */
@EnabledIfEnvironmentVariable(named = "TEST_PG_URL", matches = ".+")
class EntitlementPostgresTest {

    private static final Operator OPERATOR = new Operator(UUID.randomUUID(), "ops@platform.test");
    private static final Pattern KEY = Pattern.compile("^[a-z][a-z0-9]*(\\.[a-z][a-z0-9_]*){2,}$");

    private static JdbcClient jdbc;
    private static TenantRepository repo;
    private static PackageService packages;
    private static EntitlementService entitlements;

    @BeforeAll
    static void migrate() {
        DriverManagerDataSource ds = new DriverManagerDataSource(System.getenv("TEST_PG_URL"), System.getenv("TEST_PG_ADMIN_USER"), System.getenv("TEST_PG_ADMIN_PASSWORD"));
        Flyway.configure().dataSource(ds).cleanDisabled(false).load().clean();
        Flyway.configure().dataSource(ds).load().migrate();
        jdbc = JdbcClient.create(ds);
        repo = new TenantRepository(jdbc);
        PlatformAudit platform = new PlatformAudit(jdbc);
        packages = new PackageService(jdbc, platform);
        entitlements = new EntitlementService(jdbc, packages, platform, new AuditRecorder(jdbc, tools.jackson.databind.json.JsonMapper.builder().build()));
    }

    @BeforeEach
    void clean() {
        jdbc.sql("DELETE FROM audit_outbox").update();
        jdbc.sql("DELETE FROM tenants").update();
        jdbc.sql("DELETE FROM features WHERE key LIKE '%.zz_%'").update();
        jdbc.sql("DELETE FROM packages WHERE version > 1").update();
        jdbc.sql("UPDATE packages SET status = 'active'").update();
    }

    private UUID tenant(String name, String pkg) {
        Tenant t = repo.insert(name, name.toUpperCase(), null, null, "admin@" + name + ".test", "ENTERPRISE", null, "NONE");
        if (!"lab-full".equals(pkg)) {
            entitlements.changePackage(t.tenantId(), pkg, OPERATOR);
        }
        return t.tenantId();
    }

    private static List<Resolved> all(TenantEntitlements e) {
        return e.groups().stream().flatMap(g -> g.features().stream()).toList();
    }

    private static List<Resolved> of(TenantEntitlements e, String product) {
        return all(e).stream().filter(f -> f.product().equals(product)).toList();
    }

    private static void assertStatus(Runnable r, HttpStatus status) {
        assertThatThrownBy(r::run).isInstanceOfSatisfying(ResponseStatusException.class, x -> assertThat(x.getStatusCode()).isEqualTo(status));
    }

    @Test
    void theCatalogueHasThreeProductsTenGroupsAndSixtyFiveFeatures() {
        assertThat(entitlements.products()).extracting(EntitlementService.Product::key).containsExactly("platform", "itsm", "automation");
        assertThat(entitlements.products().get(0).mandatory()).isTrue();
        assertThat(entitlements.groups()).hasSize(10);
        List<EntitlementService.Feat> features = entitlements.features();
        assertThat(features).hasSize(65);
        features.forEach(f -> {
            assertThat(f.key()).matches(KEY);
            assertThat(f.key()).startsWith(f.group() + ".");                       // the key says which group and Product a feature belongs to
            assertThat(f.key()).startsWith(f.product() + ".");
            assertThat(f.defaultOn()).isTrue();
            assertThat(f.enforced()).isFalse();
        });
        assertThat(entitlements.catalogue()).hasSize(3);
    }

    @Test
    void aTenantOnTheManagedLabPackageHasEverythingInItsTwoProducts() {
        TenantEntitlements e = entitlements.entitlements(tenant("acme", "lab-full"));
        assertThat(e.pkg().key()).isEqualTo("lab-full");
        assertThat(e.products()).extracting(p -> p.key() + ":" + p.on()).containsExactly("platform:true", "itsm:true", "automation:true");
        assertThat(all(e)).hasSize(65).allMatch(Resolved::usable);
        assertThat(of(e, "platform")).allMatch(f -> f.layer().equals("mandatory"));
        assertThat(of(e, "itsm")).allMatch(f -> f.layer().equals("package"));
    }

    @Test
    void aProductNotInThePackageIsOffForEveryFeatureInIt() {
        TenantEntitlements e = entitlements.entitlements(tenant("itsmonly", "itsm"));
        assertThat(e.products()).extracting(p -> p.key() + ":" + p.on()).containsExactly("platform:true", "itsm:true", "automation:false");
        assertThat(of(e, "automation")).isNotEmpty().noneMatch(Resolved::usable).allMatch(f -> f.source().equals("Product not in package"));
        assertThat(of(e, "itsm")).allMatch(Resolved::usable);
        assertThat(of(e, "platform")).allMatch(Resolved::usable);
    }

    @Test
    void aProductCanBeAddedForOneTenantByAnOverrideAndRemovedAgain() {
        UUID t = tenant("addon", "itsm");
        TenantEntitlements e = entitlements.override(t, "product", "automation", true, "Pilot of AWS Automation", LocalDate.now().plusDays(30), OPERATOR);
        assertThat(of(e, "automation")).isNotEmpty().allMatch(Resolved::usable).allMatch(f -> f.source().equals("Tenant product add-on") && f.override());
        assertThat(of(e, "automation").get(0).reason()).isEqualTo("Pilot of AWS Automation");
        assertThat(e.products().stream().filter(p -> p.key().equals("automation")).findFirst().orElseThrow().override().on()).isTrue();
        assertThat(entitlements.states(t).stream().filter(s -> s.product().equals("automation"))).isNotEmpty().allMatch(s -> s.effective());

        e = entitlements.removeOverride(t, "product", "automation", OPERATOR);
        assertThat(of(e, "automation")).noneMatch(Resolved::usable);
    }

    @Test
    void aProductSwitchedOffOverridesEverythingInsideIt() {
        UUID t = tenant("noitsm", "lab-full");
        entitlements.override(t, "feature", "itsm.tickets.tickets_view_own", true, "Explicitly on", null, OPERATOR);
        TenantEntitlements e = entitlements.override(t, "product", "itsm", false, "Contract paused", null, OPERATOR);
        assertThat(of(e, "itsm")).isNotEmpty().noneMatch(Resolved::usable);                 // not even the feature that has its own override
        assertThat(of(e, "automation")).allMatch(Resolved::usable);
        assertThat(of(e, "platform")).allMatch(Resolved::usable);
        assertThat(entitlements.states(t).stream().filter(s -> s.product().equals("itsm"))).noneMatch(s -> s.effective());
    }

    @Test
    void thePlatformProductIsAlwaysOnAndCannotBeSwitched() {
        UUID t = tenant("plat", "automation");
        assertStatus(() -> entitlements.override(t, "product", "platform", false, "no", null, OPERATOR), HttpStatus.BAD_REQUEST);
        assertStatus(() -> entitlements.override(t, "group", "platform.administration", false, "no", null, OPERATOR), HttpStatus.BAD_REQUEST);
        assertStatus(() -> entitlements.override(t, "feature", entitlements.features().stream().filter(f -> f.product().equals("platform")).findFirst().orElseThrow().key(), false, "no", null, OPERATOR), HttpStatus.BAD_REQUEST);
        assertThat(of(entitlements.entitlements(t), "platform")).allMatch(Resolved::usable);
    }

    @Test
    void anOverrideNeedsAReasonAndCannotExpireInThePast() {
        UUID t = tenant("rules", "lab-full");
        assertStatus(() -> entitlements.override(t, "group", "itsm.reports", false, "  ", null, OPERATOR), HttpStatus.BAD_REQUEST);
        assertStatus(() -> entitlements.override(t, "group", "itsm.reports", false, "ok", LocalDate.now().minusDays(1), OPERATOR), HttpStatus.BAD_REQUEST);
        assertStatus(() -> entitlements.override(t, "product", "nope", false, "ok", null, OPERATOR), HttpStatus.NOT_FOUND);
        assertStatus(() -> entitlements.override(t, "colour", "itsm", false, "ok", null, OPERATOR), HttpStatus.BAD_REQUEST);
    }

    @Test
    void aGroupAndAFeatureCanBeSwitchedWithinAProduct() {
        UUID t = tenant("granular", "lab-full");
        entitlements.override(t, "group", "itsm.reports", false, "Reports not licensed", null, OPERATOR);
        TenantEntitlements e = entitlements.override(t, "feature", "itsm.tickets.tickets_comment", false, "Disabled for this tenant", null, OPERATOR);
        assertThat(all(e).stream().filter(f -> f.key().startsWith("itsm.reports."))).isNotEmpty().noneMatch(Resolved::usable);
        assertThat(all(e).stream().filter(f -> f.key().equals("itsm.tickets.tickets_comment")).findFirst().orElseThrow().usable()).isFalse();
        assertThat(all(e).stream().filter(f -> f.key().equals("itsm.tickets.tickets_view_own")).findFirst().orElseThrow().usable()).isTrue();
        GroupView reports = e.groups().stream().filter(g -> g.key().equals("itsm.reports")).findFirst().orElseThrow();
        assertThat(reports.on()).isFalse();
    }

    @Test
    void anExpiredOverrideIsIgnored() {
        UUID t = tenant("expiry", "lab-full");
        entitlements.override(t, "group", "itsm.reports", false, "Temporary", LocalDate.now().plusDays(1), OPERATOR);
        jdbc.sql("UPDATE tenant_overrides SET until = current_date - 1 WHERE tenant_id = :t").param("t", t).update();
        assertThat(all(entitlements.entitlements(t)).stream().filter(f -> f.key().startsWith("itsm.reports."))).allMatch(Resolved::usable);
    }

    @Test
    void aFeatureThatNeedsAnotherIsBlockedWhileTheOtherIsOff() {
        jdbc.sql("INSERT INTO features (key, group_key, title, needs, default_on, sort) VALUES ('itsm.tickets.zz_base', 'itsm.tickets', 'Base', NULL, true, 90)").update();
        jdbc.sql("INSERT INTO features (key, group_key, title, needs, default_on, sort) VALUES ('itsm.tickets.zz_extra', 'itsm.tickets', 'Extra', 'itsm.tickets.zz_base', true, 91)").update();
        UUID t = tenant("needs", "lab-full");
        assertThat(all(entitlements.entitlements(t)).stream().filter(f -> f.key().equals("itsm.tickets.zz_extra")).findFirst().orElseThrow().usable()).isTrue();

        TenantEntitlements e = entitlements.override(t, "feature", "itsm.tickets.zz_base", false, "Off", null, OPERATOR);
        Resolved extra = all(e).stream().filter(f -> f.key().equals("itsm.tickets.zz_extra")).findFirst().orElseThrow();
        assertThat(extra.on()).isTrue();
        assertThat(extra.usable()).isFalse();
        assertThat(extra.blockedBy()).containsExactly("itsm.tickets.zz_base");
    }

    @Test
    void aSuspendedTenantCanUseNothing() {
        UUID t = tenant("paused", "lab-full");
        jdbc.sql("UPDATE tenants SET status = 'suspended' WHERE tenant_id = :t").param("t", t).update();
        assertThat(all(entitlements.entitlements(t))).hasSize(65).noneMatch(Resolved::usable);
        assertThat(entitlements.states(t)).noneMatch(s -> s.effective());
    }

    @Test
    void theTenantOwnSwitchAppliesOnlyToFeaturesThePlatformEnforces() {
        UUID t = tenant("ownswitch", "lab-full");
        assertStatus(() -> entitlements.setEnabled(t, "itsm.tickets.tickets_view_own", false, OPERATOR), HttpStatus.CONFLICT);   // not enforced yet
        jdbc.sql("UPDATE features SET enforced = true WHERE key = 'itsm.tickets.tickets_view_own'").update();
        var states = entitlements.setEnabled(t, "itsm.tickets.tickets_view_own", false, OPERATOR);
        assertThat(states.stream().filter(s -> s.key().equals("itsm.tickets.tickets_view_own")).findFirst().orElseThrow().effective()).isFalse();
        assertThat(states.stream().filter(s -> s.key().equals("itsm.tickets.tickets_comment")).findFirst().orElseThrow().effective()).isTrue();
        jdbc.sql("UPDATE features SET enforced = false").update();
    }

    @Test
    void aPublishedPackageIsReadOnlyAndTenantsStayOnTheirVersionUntilMoved() {
        UUID t = tenant("versions", "itsm");
        PackageService.Pkg draft = packages.newVersion("itsm", OPERATOR);
        assertThat(draft.version()).isEqualTo(2);
        assertThat(draft.products()).containsExactly("itsm");

        List<String> products = List.of("itsm", "automation");
        List<String> groups = new java.util.ArrayList<>(draft.groups());
        groups.add("automation.status");
        packages.update("itsm", 2, new PackageService.Update("with status", null, products, groups, Map.of(), null), OPERATOR);
        packages.publish("itsm", 2, OPERATOR);
        assertStatus(() -> packages.update("itsm", 2, new PackageService.Update("x", null, null, null, null, null), OPERATOR), HttpStatus.CONFLICT);

        // the tenant was not moved: it still has what version 1 gave it
        assertThat(entitlements.entitlements(t).pkg().version()).isEqualTo(1);
        assertThat(of(entitlements.entitlements(t), "automation")).noneMatch(Resolved::usable);
        assertThat(entitlements.preview(t, "itsm").gained()).isNotEmpty();

        TenantEntitlements moved = entitlements.changePackage(t, "itsm", OPERATOR);
        assertThat(moved.pkg().version()).isEqualTo(2);
        assertThat(of(moved, "automation").stream().filter(Resolved::usable).map(Resolved::key)).allMatch(k -> k.startsWith("automation.status."));
        assertThat(of(moved, "automation").stream().filter(Resolved::usable)).isNotEmpty();
    }

    @Test
    void aPackageCannotHaveThePlatformProductOrAGroupOfAProductItLacks() {
        PackageService.Pkg draft = packages.newVersion("automation", OPERATOR);
        assertStatus(() -> packages.update("automation", draft.version(), new PackageService.Update(null, null, List.of("platform", "automation"), null, null, null), OPERATOR), HttpStatus.BAD_REQUEST);
        assertStatus(() -> packages.update("automation", draft.version(), new PackageService.Update(null, null, List.of("nope"), null, null, null), OPERATOR), HttpStatus.BAD_REQUEST);
        List<String> groups = new java.util.ArrayList<>(draft.groups());
        groups.add("itsm.tickets");                                     // a group of a Product this package does not have
        assertStatus(() -> packages.update("automation", draft.version(), new PackageService.Update(null, null, null, groups, null, null), OPERATOR), HttpStatus.BAD_REQUEST);
        assertStatus(() -> packages.update("automation", draft.version(), new PackageService.Update(null, null, null, List.of("automation.nope"), null, null), OPERATOR), HttpStatus.BAD_REQUEST);
    }

    @Test
    void aPackageCannotSwitchOnAFeatureWhoseNeedIsOff() {
        jdbc.sql("INSERT INTO features (key, group_key, title, needs, default_on, sort) VALUES ('itsm.tickets.zz_base', 'itsm.tickets', 'Base', NULL, true, 90)").update();
        jdbc.sql("INSERT INTO features (key, group_key, title, needs, default_on, sort) VALUES ('itsm.tickets.zz_extra', 'itsm.tickets', 'Extra', 'itsm.tickets.zz_base', true, 91)").update();
        PackageService.Pkg draft = packages.newVersion("itsm", OPERATOR);
        assertStatus(() -> packages.update("itsm", draft.version(), new PackageService.Update(null, null, null, null, Map.of("itsm.tickets.zz_base", false), null), OPERATOR), HttpStatus.BAD_REQUEST);
    }

    @Test
    void theTenantCannotSwitchOffAPlatformFeatureEvenOnceItIsEnforced() {
        UUID t = tenant("lockout", "lab-full");
        String key = entitlements.features().stream().filter(f -> f.product().equals("platform")).findFirst().orElseThrow().key();
        jdbc.sql("UPDATE features SET enforced = true WHERE key = :k").param("k", key).update();
        assertStatus(() -> entitlements.setEnabled(t, key, false, OPERATOR), HttpStatus.BAD_REQUEST);
        assertThat(entitlements.states(t).stream().filter(x -> x.key().equals(key)).findFirst().orElseThrow().effective()).isTrue();
        jdbc.sql("UPDATE features SET enforced = false").update();
    }

    @Test
    void aMandatoryGroupInsideAProductIsStillOffWhenTheProductIsOff() {
        jdbc.sql("UPDATE feature_groups SET mandatory = true WHERE key = 'itsm.reports'").update();
        try {
            UUID t = tenant("mandgroup", "lab-full");
            TenantEntitlements on = entitlements.entitlements(t);
            assertThat(all(on).stream().filter(f -> f.key().startsWith("itsm.reports."))).allMatch(f -> f.usable() && f.layer().equals("mandatory"));
            TenantEntitlements off = entitlements.override(t, "product", "itsm", false, "Paused", null, OPERATOR);
            assertThat(all(off).stream().filter(f -> f.key().startsWith("itsm.reports."))).isNotEmpty().noneMatch(Resolved::usable);
            assertThat(off.groups().stream().filter(g -> g.key().equals("itsm.reports")).findFirst().orElseThrow().on()).isFalse();
        } finally {
            jdbc.sql("UPDATE feature_groups SET mandatory = false").update();
        }
    }

    @Test
    void aPackageFeatureSwitchOutsideItsGroupsIsRefusedAndNeverApplies() {
        PackageService.Pkg draft = packages.newVersion("automation", OPERATOR);
        assertStatus(() -> packages.update("automation", draft.version(), new PackageService.Update(null, null, null, null, Map.of("itsm.tickets.tickets_view_own", false), null), OPERATOR), HttpStatus.BAD_REQUEST);
        java.util.HashMap<String, Boolean> withNull = new java.util.HashMap<>();
        withNull.put("automation.status.status_overview", null);
        assertStatus(() -> packages.update("automation", draft.version(), new PackageService.Update(null, null, null, null, withNull, null), OPERATOR), HttpStatus.BAD_REQUEST);
        // a switch left in the table for a group the package lacks is ignored when the Product comes by override
        jdbc.sql("INSERT INTO package_features (pkg_key, pkg_version, feature, on_) VALUES ('itsm', 1, 'automation.status.status_overview', false)").update();
        UUID t = tenant("stale", "itsm");
        TenantEntitlements e = entitlements.override(t, "product", "automation", true, "Pilot", null, OPERATOR);
        assertThat(all(e).stream().filter(f -> f.key().equals("automation.status.status_overview")).findFirst().orElseThrow().usable()).isTrue();
    }

    @Test
    void theDatabaseKeepsFeatureKeysInTheirGroupAndTenantsOnARealPackage() {
        assertThatThrownBy(() -> jdbc.sql("INSERT INTO features (key, group_key, title, sort) VALUES ('platform.administration.zz_wrong', 'itsm.tickets', 'Wrong', 99)").update())
                .hasMessageContaining("must start with its group key");
        UUID t = tenant("fkcheck", "lab-full");
        assertThatThrownBy(() -> jdbc.sql("UPDATE tenants SET package_key = 'nope' WHERE tenant_id = :t").param("t", t).update()).hasMessageContaining("tenants_package_exists");
    }

    @Test
    void theCatalogueKeysAreWhatOtherServicesCheckTheirCodeAgainst() {
        EntitlementService.CatalogueKeys keys = entitlements.catalogueKeys();
        assertThat(keys.products()).containsExactlyInAnyOrder("platform", "itsm", "automation");
        assertThat(keys.features()).hasSize(65).allMatch(k -> KEY.matcher(k).matches()).contains("itsm.tickets.tickets_create_incident", "automation.status.status_overview");
    }

    @Test
    void theCatalogueTextHasNoDoubledApostrophes() {
        assertThat(entitlements.features()).noneMatch(f -> f.description().contains("''") || f.title().contains("''"));
    }

    @Test
    void everyChangeIsWrittenToThePlatformAuditTrailWhichStaysIntact() {
        UUID t = tenant("audited", "itsm");
        entitlements.override(t, "product", "automation", true, "Pilot", null, OPERATOR);
        entitlements.removeOverride(t, "product", "automation", OPERATOR);
        List<String> actions = jdbc.sql("SELECT payload->>'action' FROM audit_outbox WHERE tenant_id = :t ORDER BY id").param("t", t).query(String.class).list();
        assertThat(actions).contains("tenant.package.changed", "tenant.entitlement.overridden", "tenant.entitlement.override_removed");
        assertThat(new PlatformAudit(jdbc).verify().ok()).isTrue();
    }
}
