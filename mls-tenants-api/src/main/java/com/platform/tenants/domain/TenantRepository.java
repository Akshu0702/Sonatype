package com.platform.tenants.domain;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Every lookup is an equality on a column. There is intentionally no LIKE, ILIKE,
 * position() or regex match anywhere in this class: a fuzzy resolver was once a
 * real cross-tenant leak (PLATFORM-SPEC section 1).
 */
@Repository
public class TenantRepository {

    private static final String COLUMNS = """
            tenant_id, tenant_name, display_name, tenant_code, domain, admin_email, status, tenant_type,
            parent_tenant_id, parent_access_level, realm_name, provisioning_steps_completed, created_at
            """;

    private final JdbcClient jdbc;

    public TenantRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Tenant> findById(UUID id) {
        return one("SELECT " + COLUMNS + " FROM tenants WHERE tenant_id = :v", id);
    }

    /** Newest first. The registry is small (one row per customer), so there is no paging. */
    public java.util.List<Tenant> findAll() {
        return jdbc.sql("SELECT " + COLUMNS + " FROM tenants ORDER BY created_at DESC").query(this::map).list();
    }

    /** Changes what an operator may change about a tenant. A null value leaves the field as it is. */
    public boolean updateProfile(UUID id, String displayName, String domain, String code) {
        return jdbc.sql("UPDATE tenants SET display_name = COALESCE(:d, display_name), domain = COALESCE(:dom, domain), "
                        + "tenant_code = COALESCE(:code, tenant_code), updated_at = now() WHERE tenant_id = :id")
                .param("d", displayName).param("dom", domain).param("code", code).param("id", id).update() == 1;
    }

    /** Moves between two states only if the tenant is in the expected one. Returns false otherwise. */
    public boolean transition(UUID id, java.util.Collection<String> from, String to) {
        return jdbc.sql("UPDATE tenants SET status = :to, updated_at = now() WHERE tenant_id = :id AND status IN (:from)")
                .param("to", to).param("id", id).param("from", from).update() == 1;
    }

    public Optional<Tenant> findByCode(String code) {
        return one("SELECT " + COLUMNS + " FROM tenants WHERE tenant_code = :v", code);
    }

    public Optional<Tenant> findByDomain(String domain) {
        return one("SELECT " + COLUMNS + " FROM tenants WHERE domain = :v", domain);
    }

    public Optional<Tenant> findByName(String name) {
        return one("SELECT " + COLUMNS + " FROM tenants WHERE tenant_name = :v", name);
    }

    public Optional<Tenant> findByRealm(String realm) {
        return one("SELECT " + COLUMNS + " FROM tenants WHERE realm_name = :v", realm);
    }

    public Tenant insert(String name, String displayName, String code, String domain, String adminEmail, String type,
                         UUID parent, String accessLevel) {
        return jdbc.sql("INSERT INTO tenants (tenant_name, display_name, tenant_code, domain, admin_email, tenant_type, "
                        + "parent_tenant_id, parent_access_level, realm_name) "
                        + "VALUES (:name, :display, :code, :domain, :email, :type, :parent, :level, :name) "
                        + "RETURNING " + COLUMNS)
                .param("name", name).param("display", displayName)
                .param("code", code).param("domain", domain).param("email", adminEmail).param("type", type)
                .param("parent", parent).param("level", accessLevel)
                .query(this::map).single();
    }

    /** Takes the provisioning lease if nobody holds it. Returns false when another run is in progress. */
    public boolean acquireLease(UUID id, int seconds) {
        return jdbc.sql("UPDATE tenants SET provisioning_lease_until = now() + make_interval(secs => :s) "
                        + "WHERE tenant_id = :id AND status = 'provisioning' "
                        + "AND (provisioning_lease_until IS NULL OR provisioning_lease_until < now())")
                .param("s", seconds).param("id", id).update() == 1;
    }

    public void releaseLease(UUID id) {
        jdbc.sql("UPDATE tenants SET provisioning_lease_until = NULL WHERE tenant_id = :id").param("id", id).update();
    }

    /** Sets one step bit atomically; safe to repeat. */
    public void markStepDone(UUID id, int bit) {
        jdbc.sql("UPDATE tenants SET provisioning_steps_completed = provisioning_steps_completed | :bit, "
                        + "updated_at = now() WHERE tenant_id = :id")
                .param("bit", bit).param("id", id).update();
    }

    public void markActive(UUID id) {
        jdbc.sql("UPDATE tenants SET status = 'active', updated_at = now() WHERE tenant_id = :id AND status = 'provisioning'")
                .param("id", id).update();
    }

    private Optional<Tenant> one(String sql, Object value) {
        return jdbc.sql(sql).param("v", value).query(this::map).optional();
    }

    private Tenant map(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        return new Tenant(
                rs.getObject("tenant_id", UUID.class), rs.getString("tenant_name"), rs.getString("display_name"),
                rs.getString("tenant_code"), rs.getString("domain"), rs.getString("admin_email"), rs.getString("status"), rs.getString("tenant_type"),
                rs.getObject("parent_tenant_id", UUID.class), rs.getString("parent_access_level"),
                rs.getString("realm_name"), rs.getInt("provisioning_steps_completed"),
                rs.getTimestamp("created_at").toInstant());
    }
}
