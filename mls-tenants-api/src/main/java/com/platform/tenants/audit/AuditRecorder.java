package com.platform.tenants.audit;

import com.platform.security.audit.AuditEvent;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Writes an audit event for a platform operation into the outbox, in the caller's transaction. The event belongs to
 * the tenant it was about, so the tenant's administrators see it in their own audit log.
 */
@Component
public class AuditRecorder {

    private static final String SOURCE = "tenants-service";

    private final JdbcClient jdbc;
    private final ObjectMapper json;

    public AuditRecorder(JdbcClient jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public void record(Operator by, UUID tenantId, String action, Map<String, Object> details) {
        UUID eventId = UUID.randomUUID();
        AuditEvent event = new AuditEvent(eventId, tenantId, Instant.now(), by.id(), by.name(), action, "tenant",
                tenantId.toString(), "success", details, SOURCE);
        String payload;
        try {
            payload = json.writeValueAsString(event);
        } catch (JacksonException e) {
            throw new IllegalStateException("Cannot serialize audit event", e);
        }
        jdbc.sql("INSERT INTO audit_outbox (event_id, tenant_id, payload) VALUES (:e, :t, CAST(:p AS jsonb))")
                .param("e", eventId).param("t", tenantId).param("p", payload).update();
    }
}
