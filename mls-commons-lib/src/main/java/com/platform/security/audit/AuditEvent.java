package com.platform.security.audit;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * The audit event contract shared by every producer and the audit service.
 * {@code details} must never contain secrets (passwords, client secrets, tokens).
 *
 * @param eventId    unique per event; the audit store uses it to drop redelivered duplicates
 * @param action     dotted verb, e.g. {@code user.invited}, {@code sso.created}
 * @param outcome    {@code success} or {@code denied}
 */
public record AuditEvent(
        UUID eventId,
        UUID tenantId,
        Instant occurredAt,
        UUID actorUserId,
        String actorEmail,
        String action,
        String targetType,
        String targetId,
        String outcome,
        Map<String, Object> details,
        String source) {

    public static final String TOPIC = "audit-events";
}
