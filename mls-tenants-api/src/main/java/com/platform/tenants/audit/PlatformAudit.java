package com.platform.tenants.audit;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/**
 * The platform's own audit trail: what operators and the platform did, across tenants. Append-only (the database refuses changes) and
 * chained: each row carries the hash of the row before it, so a changed, removed or reordered row is found by {@link #verify()}.
 * The events that concern a tenant also go to that tenant's own audit log through {@link AuditRecorder}; this is the operator's view of everything.
 */
@Component
public class PlatformAudit {

    public static final String GENESIS = "genesis";
    /** Serialises writers, so rows are chained in the order they are stored. */
    private static final long LOCK = 7_240_001L;

    public record Row(long seq, Instant at, String actor, String action, UUID tenantId, String tenantName, String detail, String hash) {
    }

    public record Chain(boolean ok, long checked, Long brokenAtSeq, String headHash, Instant verifiedAt) {
    }

    private final JdbcClient jdbc;

    public PlatformAudit(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public void record(String actor, String action, UUID tenantId, String tenantName, String detail) {
        jdbc.sql("SELECT pg_advisory_xact_lock(:k)").param("k", LOCK).query().listOfRows();
        String previous = jdbc.sql("SELECT hash FROM platform_audit ORDER BY seq DESC LIMIT 1").query(String.class).optional().orElse(GENESIS);
        Instant at = Instant.now().truncatedTo(ChronoUnit.MICROS);
        String text = detail == null ? "" : detail;
        jdbc.sql("""
                INSERT INTO platform_audit (at, actor, action, tenant_id, tenant_name, detail, prev_hash, hash)
                VALUES (:at, :actor, :action, :tenant, :name, :detail, :prev, :hash)""")
                .param("at", java.sql.Timestamp.from(at)).param("actor", actor).param("action", action).param("tenant", tenantId).param("name", tenantName)
                .param("detail", text).param("prev", previous).param("hash", hash(previous, at, actor, action, tenantId, tenantName, text)).update();
    }

    @Transactional(readOnly = true)
    public List<Row> list(int limit) {
        return jdbc.sql("SELECT seq, at, actor, action, tenant_id, tenant_name, detail, hash FROM platform_audit ORDER BY seq DESC LIMIT :n")
                .param("n", Math.max(1, Math.min(limit, 500)))
                .query((rs, i) -> new Row(rs.getLong(1), rs.getTimestamp(2).toInstant(), rs.getString(3), rs.getString(4), rs.getObject(5, UUID.class), rs.getString(6),
                        rs.getString(7), rs.getString(8))).list();
    }

    /** Recomputes every hash from what is stored and says where the chain first fails, if it does. */
    @Transactional(readOnly = true)
    public Chain verify() {
        String[] previous = {GENESIS};
        long[] checked = {0};
        Long[] broken = {null};
        jdbc.sql("SELECT seq, at, actor, action, tenant_id, tenant_name, detail, prev_hash, hash FROM platform_audit ORDER BY seq").query(rs -> {
            if (broken[0] != null) {
                return;
            }
            String expected = hash(previous[0], rs.getTimestamp(2).toInstant(), rs.getString(3), rs.getString(4), rs.getObject(5, UUID.class), rs.getString(6), rs.getString(7));
            if (!previous[0].equals(rs.getString(8)) || !expected.equals(rs.getString(9))) {
                broken[0] = rs.getLong(1);
                return;
            }
            previous[0] = rs.getString(9);
            checked[0]++;
        });
        return new Chain(broken[0] == null, checked[0], broken[0], previous[0], Instant.now());
    }

    static String hash(String previous, Instant at, String actor, String action, UUID tenantId, String tenantName, String detail) {
        StringBuilder in = new StringBuilder();
        for (String f : new String[] {previous, at.truncatedTo(ChronoUnit.MICROS).toString(), actor, action, tenantId == null ? null : tenantId.toString(), tenantName, detail}) {
            if (f == null) {
                in.append("-;");
            } else {
                in.append(f.length()).append(':').append(f).append(';');
            }
        }
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(in.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
