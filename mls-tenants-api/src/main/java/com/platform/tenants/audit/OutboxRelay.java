package com.platform.tenants.audit;

import com.platform.security.audit.AuditEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Publishes pending outbox rows to Kafka, in order, at least once: a row is marked sent only after the broker
 * acknowledged it. If the broker is down the rows simply stay pending. The audit store drops duplicates by event id.
 */
@Component
@ConditionalOnProperty(prefix = "platform.audit", name = "relay-enabled", havingValue = "true", matchIfMissing = true)
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
    private static final int BATCH = 100;

    private record Row(long id, String tenantId, String payload) {
    }

    private final JdbcTemplate jdbc;
    private final KafkaTemplate<String, String> kafka;
    private final TransactionTemplate tx;

    public OutboxRelay(JdbcTemplate jdbc, KafkaTemplate<String, String> kafka, TransactionTemplate tx) {
        this.jdbc = jdbc;
        this.kafka = kafka;
        this.tx = tx;
    }

    @Scheduled(fixedDelayString = "${platform.audit.relay-interval-ms:2000}")
    public void relay() {
        try {
            tx.executeWithoutResult(status -> {
                List<Row> rows = jdbc.query("""
                        SELECT id, tenant_id::text, payload::text FROM audit_outbox
                         WHERE sent_at IS NULL ORDER BY id LIMIT ? FOR UPDATE SKIP LOCKED""",
                        (rs, n) -> new Row(rs.getLong(1), rs.getString(2), rs.getString(3)), BATCH);
                for (Row row : rows) {
                    try {
                        kafka.send(AuditEvent.TOPIC, row.tenantId(), row.payload()).get(10, TimeUnit.SECONDS);
                    } catch (Exception e) {
                        log.warn("Audit relay paused; broker did not acknowledge row {}", row.id(), e);
                        return;
                    }
                    jdbc.update("UPDATE audit_outbox SET sent_at = now() WHERE id = ?", row.id());
                }
            });
            jdbc.update("DELETE FROM audit_outbox WHERE sent_at < now() - interval '7 days'");
        } catch (RuntimeException e) {
            log.warn("Audit relay tick failed", e);
        }
    }
}
