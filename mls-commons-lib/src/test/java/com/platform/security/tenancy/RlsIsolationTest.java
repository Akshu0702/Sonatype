package com.platform.security.tenancy;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Proves the property the whole tenancy design rests on, against a REAL PostgreSQL:
 * a query with NO tenant filter returns only the caller's rows, never another
 * tenant's, even when a single pooled connection is reused across tenants.
 *
 * Runs only when TEST_PG_URL, TEST_PG_ADMIN_USER, TEST_PG_ADMIN_PASSWORD are set
 * (the CI job starts a postgres:18 service for it).
 */
@EnabledIfEnvironmentVariable(named = "TEST_PG_URL", matches = ".+")
class RlsIsolationTest {

    private static final String APP_ROLE = "rls_test_app";
    private static final String APP_PASSWORD = "rls_test_pw";
    private static final UUID ACME = UUID.randomUUID();
    private static final UUID GLOBEX = UUID.randomUUID();

    private static HikariDataSource pool;
    private static RlsConnectionProvider provider;

    @BeforeAll
    static void setUp() throws SQLException {
        String url = System.getenv("TEST_PG_URL");
        try (Connection admin = DriverManager.getConnection(url,
                System.getenv("TEST_PG_ADMIN_USER"), System.getenv("TEST_PG_ADMIN_PASSWORD"));
             Statement s = admin.createStatement()) {
            dropRole(s);
            // Not a superuser, not BYPASSRLS: behaves like a real service role.
            s.execute("CREATE ROLE " + APP_ROLE + " LOGIN PASSWORD '" + APP_PASSWORD + "' NOSUPERUSER NOBYPASSRLS");
            s.execute("GRANT CREATE ON SCHEMA public TO " + APP_ROLE);
        }
        // Connect as the app role and create the table, so the role OWNS it and
        // FORCE ROW LEVEL SECURITY is what makes the policy apply to the owner.
        try (Connection app = DriverManager.getConnection(url, APP_ROLE, APP_PASSWORD);
             Statement s = app.createStatement()) {
            s.execute("CREATE TABLE widget (id uuid PRIMARY KEY DEFAULT gen_random_uuid(), tenant_id uuid NOT NULL, name text NOT NULL)");
            s.execute("ALTER TABLE widget ENABLE ROW LEVEL SECURITY");
            s.execute("ALTER TABLE widget FORCE ROW LEVEL SECURITY");
            s.execute("CREATE POLICY tenant_isolation ON widget "
                    + "USING (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid) "
                    + "WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid)");
        }

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setUsername(APP_ROLE);
        config.setPassword(APP_PASSWORD);
        config.setMaximumPoolSize(1); // one physical connection: any leak between tenants WILL show up
        pool = new HikariDataSource(config);
        provider = new RlsConnectionProvider(pool);

        insertAs(ACME, ACME, "acme-1");
        insertAs(ACME, ACME, "acme-2");
        insertAs(GLOBEX, GLOBEX, "globex-1");
    }

    @AfterAll
    static void tearDown() throws SQLException {
        if (pool != null) {
            pool.close();
        }
        try (Connection admin = DriverManager.getConnection(System.getenv("TEST_PG_URL"),
                System.getenv("TEST_PG_ADMIN_USER"), System.getenv("TEST_PG_ADMIN_PASSWORD"));
             Statement s = admin.createStatement()) {
            dropRole(s);
        }
    }

    /** Removes the role together with the table it owns and the schema grant it holds. */
    private static void dropRole(Statement admin) throws SQLException {
        admin.execute("DO $$ BEGIN IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = '" + APP_ROLE + "') THEN "
                + "DROP OWNED BY " + APP_ROLE + "; DROP ROLE " + APP_ROLE + "; END IF; END $$");
    }

    @Test
    void aQueryWithNoTenantFilterOnlyReturnsTheCallersRows() throws SQLException {
        assertThat(namesAs(ACME.toString())).containsExactlyInAnyOrder("acme-1", "acme-2");
        assertThat(namesAs(GLOBEX.toString())).containsExactly("globex-1");
    }

    @Test
    void noTenantContextSeesNothing() throws SQLException {
        assertThat(namesAs(TenantIdentifierResolver.NO_TENANT)).isEmpty();
    }

    @Test
    void aReusedPooledConnectionDoesNotCarryTheTenantOver() throws SQLException {
        namesAs(ACME.toString());                       // acme uses the only connection, releases it...
        assertThat(namesAs(TenantIdentifierResolver.NO_TENANT)).isEmpty(); // ...and the next caller must see nothing
        assertThat(settingAs(TenantIdentifierResolver.NO_TENANT)).isEmpty();
    }

    @Test
    void cannotInsertARowForAnotherTenant() {
        assertThatThrownBy(() -> insertAs(ACME, GLOBEX, "smuggled"))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("row-level security");
    }

    @Test
    void cannotUpdateOrDeleteAnotherTenantsRows() throws SQLException {
        Connection c = provider.getConnection(ACME.toString());
        try (Statement s = c.createStatement()) {
            assertThat(s.executeUpdate("UPDATE widget SET name = 'hacked' WHERE name = 'globex-1'")).isZero();
            assertThat(s.executeUpdate("DELETE FROM widget WHERE name = 'globex-1'")).isZero();
        } finally {
            provider.releaseConnection(ACME.toString(), c);
        }
        assertThat(namesAs(GLOBEX.toString())).containsExactly("globex-1");
    }

    // ---- helpers ----

    private static void insertAs(UUID actingTenant, UUID rowTenant, String name) throws SQLException {
        Connection c = provider.getConnection(actingTenant.toString());
        try (Statement s = c.createStatement()) {
            s.executeUpdate("INSERT INTO widget (tenant_id, name) VALUES ('" + rowTenant + "', '" + name + "')");
        } finally {
            provider.releaseConnection(actingTenant.toString(), c);
        }
    }

    private static java.util.List<String> namesAs(String tenantIdentifier) throws SQLException {
        Connection c = provider.getConnection(tenantIdentifier);
        try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery("SELECT name FROM widget")) {
            java.util.List<String> names = new java.util.ArrayList<>();
            while (rs.next()) {
                names.add(rs.getString(1));
            }
            return names;
        } finally {
            provider.releaseConnection(tenantIdentifier, c);
        }
    }

    private static String settingAs(String tenantIdentifier) throws SQLException {
        Connection c = provider.getConnection(tenantIdentifier);
        try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery("SELECT current_setting('app.current_tenant', true)")) {
            rs.next();
            return rs.getString(1);
        } finally {
            provider.releaseConnection(tenantIdentifier, c);
        }
    }
}
