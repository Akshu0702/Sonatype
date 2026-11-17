package com.platform.tenants.features;

import com.platform.tenants.audit.AuditRecorder;
import com.platform.tenants.audit.Operator;
import com.platform.tenants.audit.PlatformAudit;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * What a tenant may use, resolved in layers, the later winning: the Product (in the tenant's package, or switched by a tenant override; a Product that is
 * off is off for everything in it), the feature's own default, the package's groups and feature settings, a tenant override of a whole group, a tenant
 * override of one feature. The platform Product is mandatory: always on. A feature is usable when it is on, the feature it needs is usable, and the
 * tenant is active. It is live ("effective") when it is also not turned off by the tenant's own administrator.
 * Overrides carry a reason and may expire; they never change the package.
 */
@Service
public class EntitlementService {

    /** Limits a package sets. {@code enforcement}: hard stop, bill the overage, or warn only. */
    public record LimitDef(String key, String title, String enforcement) {
    }

    public static final List<LimitDef> LIMITS = List.of(
            new LimitDef("api.requests_per_minute", "API requests per minute", "hard"),
            new LimitDef("users.max_active", "Active users", "soft"),
            new LimitDef("tickets.per_month", "Tickets raised per month", "soft"),
            new LimitDef("environments.max_active", "Environments running at once", "hard"),
            new LimitDef("deployments.per_month", "Deployments per month", "soft"),
            new LimitDef("storage.max_gb", "Storage (GB)", "soft"));

    public record Product(String key, String name, boolean mandatory) {
    }

    public record Group(String key, String product, String name, boolean mandatory) {
    }

    public record Feat(String key, String group, String product, String title, String description, String needs, boolean defaultOn, boolean enforced) {
    }

    public record Override(boolean on, String reason, LocalDate until) {
    }

    /** One feature for one tenant, with where its answer came from. */
    public record Resolved(String key, String product, String title, String description, String needs, boolean enforced, boolean on, boolean usable, String source, String layer,
                           boolean override, String reason, LocalDate until, List<String> blockedBy) {
    }

    public record ProductView(String key, String name, boolean mandatory, boolean inPackage, boolean on, Override override) {
    }

    public record GroupView(String key, String product, String name, boolean mandatory, boolean inPackage, boolean on, Override override, List<Resolved> features) {
    }

    public record LimitView(String key, String title, String enforcement, Long packageValue, boolean packageUnlimited, Long overrideValue, boolean overridden, String overrideReason) {
    }

    public record PackageRef(String key, int version, String name, String type, String price) {
    }

    public record TenantEntitlements(PackageRef pkg, String tenantStatus, List<ProductView> products, List<GroupView> groups, List<LimitView> limits) {
    }

    public record Change(List<String> gained, List<String> lost, List<String> overridesKept) {
    }

    private final JdbcClient jdbc;
    private final PackageService packages;
    private final PlatformAudit platform;
    private final AuditRecorder audit;

    public EntitlementService(JdbcClient jdbc, PackageService packages, PlatformAudit platform, AuditRecorder audit) {
        this.jdbc = jdbc;
        this.packages = packages;
        this.platform = platform;
        this.audit = audit;
    }

    // ------------------------------------------------------------------ the catalogue

    /** Just the keys: what the other services check the feature keys in their code against at startup. */
    public record CatalogueKeys(List<String> products, List<String> features) {
    }

    public CatalogueKeys catalogueKeys() {
        return new CatalogueKeys(products().stream().map(Product::key).toList(), features().stream().map(Feat::key).toList());
    }

    public List<Product> products() {
        return jdbc.sql("SELECT key, name, mandatory FROM products ORDER BY sort").query((rs, n) -> new Product(rs.getString(1), rs.getString(2), rs.getBoolean(3))).list();
    }

    public List<Group> groups() {
        return jdbc.sql("SELECT g.key, g.product_key, g.name, g.mandatory FROM feature_groups g JOIN products p ON p.key = g.product_key ORDER BY p.sort, g.sort")
                .query((rs, n) -> new Group(rs.getString(1), rs.getString(2), rs.getString(3), rs.getBoolean(4))).list();
    }

    public List<Feat> features() {
        return jdbc.sql("SELECT f.key, f.group_key, g.product_key, f.title, f.description, f.needs, f.default_on, f.enforced FROM features f JOIN feature_groups g ON g.key = f.group_key JOIN products p ON p.key = g.product_key ORDER BY p.sort, g.sort, f.sort")
                .query((rs, n) -> new Feat(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6), rs.getBoolean(7), rs.getBoolean(8))).list();
    }

    public record CatalogueFeature(String key, String title, String description, String needs, boolean defaultOn, boolean enforced) {
    }

    public record CatalogueGroup(String key, String name, boolean mandatory, List<CatalogueFeature> features) {
    }

    public record CatalogueProduct(String key, String name, boolean mandatory, List<CatalogueGroup> groups) {
    }

    /** The whole catalogue, Product by Product, for the package editor. */
    public List<CatalogueProduct> catalogue() {
        List<Feat> all = features();
        List<Group> groups = groups();
        return products().stream().map(p -> new CatalogueProduct(p.key(), p.name(), p.mandatory(),
                groups.stream().filter(g -> g.product().equals(p.key())).map(g -> new CatalogueGroup(g.key(), g.name(), g.mandatory(),
                        all.stream().filter(f -> f.group().equals(g.key())).map(f -> new CatalogueFeature(f.key(), f.title(), f.description(), f.needs(), f.defaultOn(), f.enforced())).toList())).toList())).toList();
    }

    // ------------------------------------------------------------------ resolution

    private record Tenant(UUID id, String name, String status, String packageKey, int packageVersion) {
    }

    private Tenant tenant(UUID id) {
        return jdbc.sql("SELECT tenant_id, tenant_name, status, package_key, package_version FROM tenants WHERE tenant_id = :id").param("id", id)
                .query((rs, n) -> new Tenant(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), rs.getString(4), rs.getInt(5))).optional()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown tenant"));
    }

    /** A tenant's overrides, by kind. */
    private record Overrides(Map<String, Override> product, Map<String, Override> group, Map<String, Override> feature) {
    }

    private Overrides overridesOf(UUID tenantId) {
        return new Overrides(overrides(tenantId, "product"), overrides(tenantId, "group"), overrides(tenantId, "feature"));
    }

    private Map<String, Override> overrides(UUID tenantId, String kind) {
        Map<String, Override> out = new HashMap<>();
        jdbc.sql("SELECT key, on_, reason, until FROM tenant_overrides WHERE tenant_id = :id AND kind = :k AND (until IS NULL OR until >= current_date)")
                .param("id", tenantId).param("k", kind)
                .query((rs, n) -> {
                    out.put(rs.getString(1), new Override(rs.getBoolean(2), rs.getString(3), rs.getObject(4, LocalDate.class)));
                    return null;
                }).list();
        return out;
    }

    /** Everything about one tenant's features, laid out by group. */
    @Transactional(readOnly = true)
    public TenantEntitlements entitlements(UUID tenantId) {
        Tenant t = tenant(tenantId);
        PackageService.Pkg p = packages.get(t.packageKey(), t.packageVersion());
        return layout(t, p, overridesOf(tenantId), tenantLimits(tenantId));
    }

    private TenantEntitlements layout(Tenant t, PackageService.Pkg p, Overrides ov, Map<String, Object[]> limitOverrides) {
        Map<String, Resolved> resolved = resolve(t, p, ov);
        List<Feat> catalogue = features();
        Map<String, Boolean> productOn = new HashMap<>();
        List<ProductView> products = new ArrayList<>();
        for (Product pr : products()) {
            boolean on = productOn(pr, p, ov);
            productOn.put(pr.key(), on);
            products.add(new ProductView(pr.key(), pr.name(), pr.mandatory(), p.products().contains(pr.key()), on, ov.product().get(pr.key())));
        }
        List<GroupView> groups = new ArrayList<>();
        for (Group g : groups()) {
            Override go = ov.group().get(g.key());
            boolean inPackage = p.products().contains(g.product()) && p.groups().contains(g.key());
            boolean on = productOn.get(g.product()) && (g.mandatory() || (go != null ? go.on() : groupIncluded(g, p)));
            groups.add(new GroupView(g.key(), g.product(), g.name(), g.mandatory(), inPackage, on, go,
                    catalogue.stream().filter(f -> f.group().equals(g.key())).map(f -> resolved.get(f.key())).toList()));
        }
        List<LimitView> limits = new ArrayList<>();
        for (LimitDef d : LIMITS) {
            PackageService.Limit pl = p.limits().stream().filter(l -> l.key().equals(d.key())).findFirst().orElse(null);
            Object[] lo = limitOverrides.get(d.key());
            limits.add(new LimitView(d.key(), d.title(), d.enforcement(), pl == null ? null : pl.value(), pl == null || pl.value() == null, lo == null ? null : (Long) lo[0], lo != null,
                    lo == null ? null : (String) lo[1]));
        }
        return new TenantEntitlements(new PackageRef(p.key(), p.version(), p.name(), p.type(), p.price()), t.status(), products, groups, limits);
    }

    /** Layer 1: a Product is on when it is mandatory, or a tenant override says so, or the package includes it. */
    private boolean productOn(Product pr, PackageService.Pkg p, Overrides ov) {
        Override o = ov.product().get(pr.key());
        return pr.mandatory() || (o != null ? o.on() : p.products().contains(pr.key()));
    }

    /** A group the package has, or, for a Product the tenant has only through an override, every group of it. */
    private boolean groupIncluded(Group g, PackageService.Pkg p) {
        return !p.products().contains(g.product()) || p.groups().contains(g.key());
    }

    private Map<String, Object[]> tenantLimits(UUID tenantId) {
        Map<String, Object[]> out = new HashMap<>();
        jdbc.sql("SELECT limit_key, value, reason FROM tenant_limit_overrides WHERE tenant_id = :id").param("id", tenantId)
                .query((rs, n) -> {
                    String limitKey = rs.getString(1);
                    long v = rs.getLong(2);
                    boolean unlimited = rs.wasNull();
                    out.put(limitKey, new Object[] {unlimited ? null : v, rs.getString(3)});
                    return null;
                }).list();
        return out;
    }

    /** The layers, in order. Features are listed after the ones they need, so one pass settles the dependencies. */
    private Map<String, Resolved> resolve(Tenant t, PackageService.Pkg p, Overrides ov) {
        Map<String, Group> groupByKey = new HashMap<>();
        groups().forEach(g -> groupByKey.put(g.key(), g));
        Map<String, Product> productByKey = new HashMap<>();
        products().forEach(x -> productByKey.put(x.key(), x));
        boolean tenantActive = "active".equals(t.status()) || "provisioning".equals(t.status());
        Map<String, Resolved> out = new LinkedHashMap<>();
        for (Feat f : features()) {
            Group g = groupByKey.get(f.group());
            Product pr = productByKey.get(f.product());
            boolean inPackage = p.products().contains(pr.key());
            boolean packageSets = inPackage && p.groups().contains(f.group()) && p.features().containsKey(f.key());
            boolean packageSwitch = packageSets ? p.features().get(f.key()) : f.defaultOn();
            boolean on;
            String source;
            String layer;
            Override po = ov.product().get(pr.key());
            Override go = ov.group().get(f.group());
            Override fo = ov.feature().get(f.key());
            if (pr.mandatory()) {
                on = true; source = "Mandatory"; layer = "mandatory"; fo = null; go = null; po = null;
            } else if (!productOn(pr, p, ov)) {
                // a Product that is off is off for everything in it, whatever a group or feature override says
                on = false; source = po != null ? "Tenant product off" : "Product not in package"; layer = po != null ? "tenant_product" : "none";
                fo = null; go = null;
            } else if (g.mandatory()) {
                on = true; source = "Mandatory"; layer = "mandatory"; fo = null; go = null;
            } else if (go != null) {
                on = go.on() && packageSwitch; source = go.on() ? "Tenant group add-on" : "Tenant group off"; layer = "tenant_group";
            } else if (groupIncluded(g, p)) {
                on = packageSwitch;
                boolean viaProduct = po != null && !inPackage;
                source = viaProduct ? "Tenant product add-on" : packageSets ? "Package: " + p.name() : "Group default (" + p.name() + ")";
                layer = viaProduct ? "tenant_product" : "package";
            } else {
                on = false; source = "Not in package"; layer = "none";
            }
            if (fo != null) {
                on = fo.on(); source = "Tenant override" + (fo.until() == null ? "" : " until " + fo.until()); layer = "tenant_feature";
            }
            // a feature needs one other, which itself carries what it needs: so "usable" of that one already settles the chain
            Resolved dep = f.needs() == null ? null : out.get(f.needs());
            List<String> blocked = f.needs() != null && (dep == null || !dep.usable()) ? List.of(f.needs()) : List.of();
            boolean usable = on && blocked.isEmpty() && tenantActive;
            Override shown = fo != null ? fo : go != null ? go : layer.startsWith("tenant") ? po : null;
            out.put(f.key(), new Resolved(f.key(), f.product(), f.title(), f.description(), f.needs(), f.enforced(), on, usable, source, layer, layer.startsWith("tenant"),
                    shown == null ? null : shown.reason(), shown == null ? null : shown.until(), blocked));
        }
        return out;
    }

    /** What a tenant would have on another package, with its overrides kept. */
    @Transactional(readOnly = true)
    public Change preview(UUID tenantId, String packageKey) {
        Tenant t = tenant(tenantId);
        PackageService.Pkg now = packages.get(t.packageKey(), t.packageVersion());
        PackageService.Pkg next = packages.active(packageKey).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No such package"));
        Overrides ov = overridesOf(tenantId);
        Map<String, Resolved> before = resolve(t, now, ov);
        Map<String, Resolved> after = resolve(t, next, ov);
        List<String> gained = new ArrayList<>();
        List<String> lost = new ArrayList<>();
        after.forEach((k, r) -> {
            boolean was = before.get(k).usable();
            if (r.usable() && !was) {
                gained.add(r.title());
            } else if (!r.usable() && was) {
                lost.add(r.title());
            }
        });
        List<String> kept = new ArrayList<>();
        ov.product().keySet().forEach(k -> kept.add(k));
        ov.group().keySet().forEach(k -> kept.add(k));
        ov.feature().keySet().forEach(k -> kept.add(k));
        return new Change(gained, lost, kept);
    }

    // ------------------------------------------------------------------ what other services ask

    /** One feature as a tenant (and the services acting for it) sees it. */
    public record State(String key, String product, String title, String description, String group, List<String> needs, boolean enforced, boolean entitled, boolean enabled, boolean effective) {
    }

    /** Every feature with the tenant's own switch applied: this is what the platform's services and the tenant administrator read. */
    @Transactional(readOnly = true)
    public List<State> states(UUID tenantId) {
        Tenant t = tenant(tenantId);
        PackageService.Pkg p = packages.get(t.packageKey(), t.packageVersion());
        Map<String, Resolved> resolved = resolve(t, p, overridesOf(tenantId));
        Set<String> off = new HashSet<>();
        jdbc.sql("SELECT feature FROM tenant_features WHERE tenant_id = :id AND NOT enabled").param("id", tenantId).query((rs, n) -> off.add(rs.getString(1))).list();
        Map<String, Boolean> live = new HashMap<>();
        List<State> out = new ArrayList<>();
        Map<String, String> groupName = new HashMap<>();
        groups().forEach(g -> groupName.put(g.key(), g.name()));
        for (Feat f : features()) {
            Resolved r = resolved.get(f.key());
            boolean enabled = !off.contains(f.key());
            // the tenant's own switch applies to the features the platform enforces; for the rest it has nothing to turn off
            boolean mandatory = "mandatory".equals(r.layer());
            boolean effective = r.usable() && (enabled || !f.enforced() || mandatory) && (f.needs() == null || Boolean.TRUE.equals(live.get(f.needs())));
            live.put(f.key(), effective);
            out.add(new State(f.key(), f.product(), f.title(), f.description(), groupName.get(f.group()), f.needs() == null ? List.of() : List.of(f.needs()), f.enforced(), r.on(), enabled, effective));
        }
        return out;
    }

    // ------------------------------------------------------------------ the operator's changes

    @Transactional
    public TenantEntitlements override(UUID tenantId, String kind, String key, boolean on, String reason, LocalDate until, Operator by) {
        Tenant t = tenant(tenantId);
        if (reason == null || reason.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A reason is required; it is written to the audit log");
        }
        if (until != null && until.isBefore(LocalDate.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "An override cannot expire in the past");
        }
        requireOverridable(kind, key);
        jdbc.sql("""
                INSERT INTO tenant_overrides (tenant_id, kind, key, on_, reason, until, updated_by) VALUES (:id, :k, :key, :on, :why, :until, :by)
                ON CONFLICT (tenant_id, kind, key) DO UPDATE SET on_ = :on, reason = :why, until = :until, updated_by = :by, updated_at = now()""")
                .param("id", tenantId).param("k", kind).param("key", key).param("on", on).param("why", reason.trim()).param("until", until).param("by", by.name()).update();
        String detail = key + (on ? " on" : " off") + (until == null ? "" : " until " + until) + " (" + reason.trim() + ")";
        audit.record(by, tenantId, "tenant.entitlement.overridden", Map.of("kind", kind, "key", key, "on", on, "reason", reason.trim()));
        platform.record(by.name(), "entitlement." + kind + ".override", tenantId, t.name(), detail);
        return entitlements(tenantId);
    }

    @Transactional
    public TenantEntitlements removeOverride(UUID tenantId, String kind, String key, Operator by) {
        Tenant t = tenant(tenantId);
        requireOverridable(kind, key);
        jdbc.sql("DELETE FROM tenant_overrides WHERE tenant_id = :id AND kind = :k AND key = :key").param("id", tenantId).param("k", kind).param("key", key).update();
        audit.record(by, tenantId, "tenant.entitlement.override_removed", Map.of("kind", kind, "key", key));
        platform.record(by.name(), "entitlement." + kind + ".override_removed", tenantId, t.name(), key + " back to the package");
        return entitlements(tenantId);
    }

    @Transactional
    public TenantEntitlements overrideLimit(UUID tenantId, String limitKey, Long value, String reason, Operator by) {
        Tenant t = tenant(tenantId);
        if (LIMITS.stream().noneMatch(l -> l.key().equals(limitKey))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown limit");
        }
        if (reason == null || reason.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A reason is required; it is written to the audit log");
        }
        if (value != null && value < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A limit cannot be negative");
        }
        jdbc.sql("""
                INSERT INTO tenant_limit_overrides (tenant_id, limit_key, value, reason, updated_by) VALUES (:id, :k, :v, :why, :by)
                ON CONFLICT (tenant_id, limit_key) DO UPDATE SET value = :v, reason = :why, updated_by = :by, updated_at = now()""")
                .param("id", tenantId).param("k", limitKey).param("v", value).param("why", reason.trim()).param("by", by.name()).update();
        audit.record(by, tenantId, "tenant.limit.overridden", Map.of("limit", limitKey, "value", value == null ? "unlimited" : value, "reason", reason.trim()));
        platform.record(by.name(), "entitlement.limit.override", tenantId, t.name(), limitKey + " = " + (value == null ? "unlimited" : value) + " (" + reason.trim() + ")");
        return entitlements(tenantId);
    }

    @Transactional
    public TenantEntitlements removeLimitOverride(UUID tenantId, String limitKey, Operator by) {
        Tenant t = tenant(tenantId);
        jdbc.sql("DELETE FROM tenant_limit_overrides WHERE tenant_id = :id AND limit_key = :k").param("id", tenantId).param("k", limitKey).update();
        platform.record(by.name(), "entitlement.limit.override_removed", tenantId, t.name(), limitKey + " back to the package");
        return entitlements(tenantId);
    }

    /** Moves a tenant to the live version of a package. */
    @Transactional
    public TenantEntitlements changePackage(UUID tenantId, String packageKey, Operator by) {
        Tenant t = tenant(tenantId);
        PackageService.Pkg next = packages.active(packageKey).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No such package"));
        jdbc.sql("UPDATE tenants SET package_key = :k, package_version = :v, updated_at = now() WHERE tenant_id = :id")
                .param("k", next.key()).param("v", next.version()).param("id", tenantId).update();
        audit.record(by, tenantId, "tenant.package.changed", Map.of("from", t.packageKey() + " v" + t.packageVersion(), "to", next.key() + " v" + next.version()));
        platform.record(by.name(), "tenant.package.changed", tenantId, t.name(), t.packageKey() + " v" + t.packageVersion() + " to " + next.name() + " v" + next.version());
        return entitlements(tenantId);
    }

    /** The tenant's own administrator turns a feature the platform enforces on or off. */
    @Transactional
    public List<State> setEnabled(UUID tenantId, String key, boolean enabled, Operator by) {
        tenant(tenantId);
        Feat f = features().stream().filter(x -> x.key().equals(key)).findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown feature"));
        if (groups().stream().anyMatch(g -> g.key().equals(f.group()) && g.mandatory()) || products().stream().anyMatch(x -> x.key().equals(f.product()) && x.mandatory())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A mandatory feature cannot be switched off");
        }
        Tenant t = tenant(tenantId);
        Resolved r = resolve(t, packages.get(t.packageKey(), t.packageVersion()), overridesOf(tenantId)).get(key);
        if (!f.enforced() || !r.on()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This feature is not part of your organisation's plan");
        }
        jdbc.sql("""
                INSERT INTO tenant_features (tenant_id, feature, enabled, updated_by) VALUES (:id, :f, :v, :by)
                ON CONFLICT (tenant_id, feature) DO UPDATE SET enabled = :v, updated_by = :by, updated_at = now()""")
                .param("id", tenantId).param("f", key).param("v", enabled).param("by", by.name()).update();
        audit.record(by, tenantId, enabled ? "tenant.feature.enabled" : "tenant.feature.disabled", Map.of("feature", key));
        return states(tenantId);
    }

    private void requireOverridable(String kind, String key) {
        if ("product".equals(kind)) {
            Product pr = products().stream().filter(x -> x.key().equals(key)).findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown product"));
            if (pr.mandatory()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The " + pr.name() + " product is always on and cannot be switched");
            }
        } else if ("group".equals(kind)) {
            Group g = groups().stream().filter(x -> x.key().equals(key)).findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown group"));
            if (g.mandatory() || products().stream().anyMatch(x -> x.key().equals(g.product()) && x.mandatory())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A mandatory group cannot be switched");
            }
        } else if ("feature".equals(kind)) {
            Feat f = features().stream().filter(x -> x.key().equals(key)).findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown feature"));
            if (groups().stream().anyMatch(g -> g.key().equals(f.group()) && g.mandatory()) || products().stream().anyMatch(x -> x.key().equals(f.product()) && x.mandatory())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A mandatory feature cannot be switched");
            }
        } else {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "kind is product, group or feature");
        }
    }

    Optional<Tenant> find(UUID id) {
        return jdbc.sql("SELECT tenant_id, tenant_name, status, package_key, package_version FROM tenants WHERE tenant_id = :id").param("id", id)
                .query((rs, n) -> new Tenant(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), rs.getString(4), rs.getInt(5))).optional();
    }
}
