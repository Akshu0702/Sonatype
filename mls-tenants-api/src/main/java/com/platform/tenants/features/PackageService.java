package com.platform.tenants.features;

import com.platform.tenants.audit.Operator;
import com.platform.tenants.audit.PlatformAudit;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Packages: versioned bundles of Products, the feature groups of each, feature settings and limits. A published version is read-only, so the tenants on it do not
 * change underneath them; a change is a new draft version, which becomes the package's live version when it is published. Tenants stay on the
 * version they have until an operator moves them.
 */
@Service
public class PackageService {

    public record Limit(String key, Long value) {
    }

    public record Pkg(String key, int version, String name, String type, String status, boolean isPublic, String price, String description, List<String> products,
                      List<String> groups, Map<String, Boolean> features, List<Limit> limits, int tenants) {
    }

    /** What a draft may change. Anything left out stays as it is. */
    public record Update(String description, String price, List<String> products, List<String> groups, Map<String, Boolean> features, List<Limit> limits) {
    }

    private final JdbcClient jdbc;
    private final PlatformAudit platform;

    public PackageService(JdbcClient jdbc, PlatformAudit platform) {
        this.jdbc = jdbc;
        this.platform = platform;
    }

    @Transactional(readOnly = true)
    public List<Pkg> list() {
        return jdbc.sql("SELECT key, version FROM packages ORDER BY name, version DESC").query((rs, n) -> new String[] {rs.getString(1), rs.getString(2)}).list().stream()
                .map(k -> get(k[0], Integer.parseInt(k[1]))).toList();
    }

    @Transactional(readOnly = true)
    public Pkg get(String key, int version) {
        return jdbc.sql("SELECT key, version, name, type, status, is_public, price, description FROM packages WHERE key = :k AND version = :v").param("k", key).param("v", version)
                .query((rs, n) -> new Pkg(rs.getString(1), rs.getInt(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getBoolean(6), rs.getString(7), rs.getString(8),
                        products(key, version), groups(key, version), features(key, version), limits(key, version), tenants(key, version))).optional()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No such package version"));
    }

    @Transactional(readOnly = true)
    public Optional<Pkg> active(String key) {
        return jdbc.sql("SELECT version FROM packages WHERE key = :k AND status = 'active'").param("k", key).query(Integer.class).optional().map(v -> get(key, v));
    }

    /** Holds the row until the transaction ends, so a draft cannot be edited and published at the same moment. */
    private void lock(String key, int version) {
        jdbc.sql("SELECT 1 FROM packages WHERE key = :k AND version = :v FOR UPDATE").param("k", key).param("v", version).query(Integer.class).optional()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No such package version"));
    }

    private List<String> products(String key, int version) {
        return jdbc.sql("SELECT product_key FROM package_products WHERE pkg_key = :k AND pkg_version = :v ORDER BY product_key").param("k", key).param("v", version).query(String.class).list();
    }

    private List<String> groups(String key, int version) {
        return jdbc.sql("SELECT group_key FROM package_groups WHERE pkg_key = :k AND pkg_version = :v ORDER BY group_key").param("k", key).param("v", version).query(String.class).list();
    }

    private Map<String, Boolean> features(String key, int version) {
        Map<String, Boolean> out = new LinkedHashMap<>();
        jdbc.sql("SELECT feature, on_ FROM package_features WHERE pkg_key = :k AND pkg_version = :v ORDER BY feature").param("k", key).param("v", version)
                .query((rs, n) -> out.put(rs.getString(1), rs.getBoolean(2))).list();
        return out;
    }

    private List<Limit> limits(String key, int version) {
        Map<String, Long> have = new HashMap<>();
        jdbc.sql("SELECT limit_key, value FROM package_limits WHERE pkg_key = :k AND pkg_version = :v").param("k", key).param("v", version)
                .query((rs, n) -> {
                    String limitKey = rs.getString(1);
                    long v = rs.getLong(2);
                    boolean unlimited = rs.wasNull();   // read straight after the value: another getter in between would reset it
                    have.put(limitKey, unlimited ? null : v);
                    return null;
                }).list();
        List<Limit> out = new ArrayList<>();
        EntitlementService.LIMITS.forEach(d -> out.add(new Limit(d.key(), have.get(d.key()))));
        return out;
    }

    private int tenants(String key, int version) {
        return jdbc.sql("SELECT count(*) FROM tenants WHERE package_key = :k AND package_version = :v AND status <> 'offboarded'").param("k", key).param("v", version).query(Integer.class).single();
    }

    /** A draft copy of the newest version, to edit. If a draft already exists it is returned. */
    @Transactional
    public Pkg newVersion(String key, Operator by) {
        Optional<Integer> draft = jdbc.sql("SELECT version FROM packages WHERE key = :k AND status = 'draft'").param("k", key).query(Integer.class).optional();
        if (draft.isPresent()) {
            return get(key, draft.get());
        }
        int latest = jdbc.sql("SELECT max(version) FROM packages WHERE key = :k").param("k", key).query(Integer.class).optional()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No such package"));
        int next = latest + 1;
        jdbc.sql("INSERT INTO packages (key, version, name, type, status, is_public, price, description) SELECT key, :n, name, type, 'draft', is_public, price, description FROM packages WHERE key = :k AND version = :v")
                .param("n", next).param("k", key).param("v", latest).update();
        jdbc.sql("INSERT INTO package_products SELECT pkg_key, :n, product_key FROM package_products WHERE pkg_key = :k AND pkg_version = :v").param("n", next).param("k", key).param("v", latest).update();
        jdbc.sql("INSERT INTO package_groups SELECT pkg_key, :n, group_key FROM package_groups WHERE pkg_key = :k AND pkg_version = :v").param("n", next).param("k", key).param("v", latest).update();
        jdbc.sql("INSERT INTO package_features SELECT pkg_key, :n, feature, on_ FROM package_features WHERE pkg_key = :k AND pkg_version = :v").param("n", next).param("k", key).param("v", latest).update();
        jdbc.sql("INSERT INTO package_limits SELECT pkg_key, :n, limit_key, value FROM package_limits WHERE pkg_key = :k AND pkg_version = :v").param("n", next).param("k", key).param("v", latest).update();
        platform.record(by.name(), "package.version_created", null, null, key + " v" + next + " started as a copy of v" + latest);
        return get(key, next);
    }

    /**
     * Edits a draft. The platform Product is always on and is in no package. A group must belong to a Product the package has, a package keeps every
     * mandatory group of its Products, and no feature may be on while one it needs is off.
     */
    @Transactional
    public Pkg update(String key, int version, Update in, Operator by) {
        lock(key, version);
        Pkg p = get(key, version);
        if (!"draft".equals(p.status())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A published package is read-only; create a new version to change it");
        }
        List<String> products = in.products() == null ? p.products() : in.products().stream().distinct().toList();
        List<String> groups = in.groups() == null ? p.groups() : in.groups().stream().distinct().toList();
        Map<String, Boolean> explicit = in.features() == null ? p.features() : in.features();
        List<EntitlementService.Product> catalogueProducts = jdbc.sql("SELECT key, name, mandatory FROM products").query((rs, n) -> new EntitlementService.Product(rs.getString(1), rs.getString(2), rs.getBoolean(3))).list();
        for (String pr : products) {
            EntitlementService.Product known = catalogueProducts.stream().filter(x -> x.key().equals(pr)).findFirst()
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown product " + pr));
            if (known.mandatory()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, known.name() + " is always on and is not part of any package");
            }
        }
        List<EntitlementService.Group> catalogueGroups = jdbc.sql("SELECT key, product_key, name, mandatory FROM feature_groups")
                .query((rs, n) -> new EntitlementService.Group(rs.getString(1), rs.getString(2), rs.getString(3), rs.getBoolean(4))).list();
        for (String g : groups) {
            EntitlementService.Group known = catalogueGroups.stream().filter(x -> x.key().equals(g)).findFirst()
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown group " + g));
            if (!products.contains(known.product())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, known.name() + " belongs to the " + known.product() + " product, which this package does not have");
            }
        }
        for (EntitlementService.Group g : catalogueGroups) {
            if (g.mandatory() && products.contains(g.product()) && !groups.contains(g.key())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, g.name() + " is mandatory and must stay in every package that has its product");
            }
        }
        List<EntitlementService.Feat> catalogue = jdbc.sql("SELECT f.key, f.group_key, g.product_key, f.title, f.description, f.needs, f.default_on, f.enforced FROM features f JOIN feature_groups g ON g.key = f.group_key")
                .query((rs, n) -> new EntitlementService.Feat(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6), rs.getBoolean(7), rs.getBoolean(8))).list();
        for (Map.Entry<String, Boolean> e : explicit.entrySet()) {
            EntitlementService.Feat known = catalogue.stream().filter(x -> x.key().equals(e.getKey())).findFirst()
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown feature " + e.getKey()));
            if (e.getValue() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Feature " + e.getKey() + " must be on or off");
            }
            if (!groups.contains(known.group())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, known.title() + " is in group " + known.group() + ", which this package does not have");
            }
        }
        for (EntitlementService.Feat f : catalogue) {
            boolean on = groups.contains(f.group()) && (explicit.containsKey(f.key()) ? explicit.get(f.key()) : f.defaultOn());
            if (on && f.needs() != null) {
                EntitlementService.Feat need = catalogue.stream().filter(x -> x.key().equals(f.needs())).findFirst().orElseThrow();
                boolean needOn = groups.contains(need.group()) && (explicit.containsKey(need.key()) ? explicit.get(need.key()) : need.defaultOn());
                if (!needOn) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, f.title() + " needs " + need.title() + ", which is off in this package");
                }
            }
        }
        jdbc.sql("UPDATE packages SET description = COALESCE(:d, description), price = COALESCE(:p, price) WHERE key = :k AND version = :v")
                .param("d", in.description()).param("p", in.price()).param("k", key).param("v", version).update();
        jdbc.sql("DELETE FROM package_groups WHERE pkg_key = :k AND pkg_version = :v").param("k", key).param("v", version).update();
        jdbc.sql("DELETE FROM package_products WHERE pkg_key = :k AND pkg_version = :v").param("k", key).param("v", version).update();
        for (String pr : products) {
            jdbc.sql("INSERT INTO package_products (pkg_key, pkg_version, product_key) VALUES (:k, :v, :p)").param("k", key).param("v", version).param("p", pr).update();
        }
        for (String g : groups) {
            jdbc.sql("INSERT INTO package_groups (pkg_key, pkg_version, group_key) VALUES (:k, :v, :g)").param("k", key).param("v", version).param("g", g).update();
        }
        jdbc.sql("DELETE FROM package_features WHERE pkg_key = :k AND pkg_version = :v").param("k", key).param("v", version).update();
        for (Map.Entry<String, Boolean> e : explicit.entrySet()) {
            jdbc.sql("INSERT INTO package_features (pkg_key, pkg_version, feature, on_) VALUES (:k, :v, :f, :on)").param("k", key).param("v", version).param("f", e.getKey()).param("on", e.getValue()).update();
        }
        if (in.limits() != null) {
            for (Limit l : in.limits()) {
                if (EntitlementService.LIMITS.stream().noneMatch(d -> d.key().equals(l.key()))) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown limit " + l.key());
                }
                if (l.value() != null && l.value() < 0) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A limit cannot be negative");
                }
                jdbc.sql("""
                        INSERT INTO package_limits (pkg_key, pkg_version, limit_key, value) VALUES (:k, :v, :l, :x)
                        ON CONFLICT (pkg_key, pkg_version, limit_key) DO UPDATE SET value = :x""").param("k", key).param("v", version).param("l", l.key()).param("x", l.value()).update();
            }
        }
        platform.record(by.name(), "package.updated", null, null, key + " v" + version + " draft edited");
        return get(key, version);
    }

    /** Makes a draft the package's live version. The version it replaces is retired; the tenants on it stay on it until moved. */
    @Transactional
    public Pkg publish(String key, int version, Operator by) {
        lock(key, version);
        Pkg p = get(key, version);
        if (!"draft".equals(p.status())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only a draft can be published");
        }
        int staying = jdbc.sql("SELECT count(*) FROM tenants t JOIN packages x ON x.key = t.package_key AND x.version = t.package_version WHERE x.key = :k AND x.status = 'active' AND t.status <> 'offboarded'")
                .param("k", key).query(Integer.class).single();
        jdbc.sql("UPDATE packages SET status = 'retired' WHERE key = :k AND status = 'active'").param("k", key).update();
        jdbc.sql("UPDATE packages SET status = 'active' WHERE key = :k AND version = :v").param("k", key).param("v", version).update();
        platform.record(by.name(), "package.published", null, null, p.name() + " v" + version + " published; " + staying + " tenants stay on the previous version until moved");
        return get(key, version);
    }
}
