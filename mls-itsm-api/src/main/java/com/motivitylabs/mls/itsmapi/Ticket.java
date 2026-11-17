package com.motivitylabs.mls.itsmapi;

import java.time.Instant;
import java.util.UUID;

/** A provisioning ticket: someone asks for an environment and uploads the build to test in it. */
public record Ticket(
        UUID id,
        UUID tenantId,
        long number,
        String title,
        String description,
        String requester,
        String requesterName,
        TicketState state,
        String config,
        int ttlHours,
        String buildName,
        String buildSha256,
        long buildSize,
        String environmentRef,
        Instant createdAt,
        Instant updatedAt) {

    /** What people see: the number is shown as TKT-1001. */
    public String reference() {
        return "TKT-" + number;
    }

    /** One line of a ticket's history. Rows are only ever added. */
    public record Event(long id, Instant at, String actor, String action, TicketState from, TicketState to, String comment) {
    }
}
