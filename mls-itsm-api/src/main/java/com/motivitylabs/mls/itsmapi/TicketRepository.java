package com.motivitylabs.mls.itsmapi;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** SQL for tickets. Every method runs inside {@link TenantDb#inTenant}, so row-level security limits it to one tenant. */
@Repository
public class TicketRepository {

    private static final String COLUMNS = "ticket_id, tenant_id, ticket_number, title, description, requester, requester_name, state, config, ttl_hours, "
            + "build_name, build_sha256, build_size, environment_ref, created_at, updated_at";

    private static Ticket map(ResultSet r, int i) throws SQLException {
        return new Ticket(r.getObject("ticket_id", UUID.class), r.getObject("tenant_id", UUID.class), r.getLong("ticket_number"), r.getString("title"),
                r.getString("description"), r.getString("requester"), r.getString("requester_name"), TicketState.valueOf(r.getString("state")),
                r.getString("config"), r.getInt("ttl_hours"), r.getString("build_name"), r.getString("build_sha256"), r.getLong("build_size"),
                r.getString("environment_ref"), r.getObject("created_at", OffsetDateTime.class).toInstant(),
                r.getObject("updated_at", OffsetDateTime.class).toInstant());
    }

    /** The next number for this tenant, starting at 1001. */
    public long nextNumber(JdbcClient j, UUID tenant) {
        return j.sql("INSERT INTO ticket_counters (tenant_id, last_number) VALUES (:t, 1001) "
                        + "ON CONFLICT (tenant_id) DO UPDATE SET last_number = ticket_counters.last_number + 1 RETURNING last_number")
                .param("t", tenant).query(Long.class).single();
    }

    public void insert(JdbcClient j, Ticket t, String buildKey) {
        j.sql("INSERT INTO tickets (ticket_id, tenant_id, ticket_number, title, description, requester, requester_name, state, config, ttl_hours, "
                        + "build_name, build_sha256, build_size, build_key, created_at, updated_at) "
                        + "VALUES (:id, :tenant, :number, :title, :description, :requester, :name, :state, :config, :ttl, :bname, :sha, :size, :bkey, :created, :created)")
                .param("id", t.id()).param("tenant", t.tenantId()).param("number", t.number()).param("title", t.title()).param("description", t.description())
                .param("requester", t.requester()).param("name", t.requesterName()).param("state", t.state().name()).param("config", t.config())
                .param("ttl", t.ttlHours()).param("bname", t.buildName()).param("sha", t.buildSha256()).param("size", t.buildSize())
                .param("bkey", buildKey).param("created", Timestamp.from(t.createdAt())).update();
    }

    public Optional<Ticket> find(JdbcClient j, UUID id) {
        return j.sql("SELECT " + COLUMNS + " FROM tickets WHERE ticket_id = :id").param("id", id).query(TicketRepository::map).optional();
    }

    /** Same, but holds the row until the transaction ends, so two decisions on one ticket cannot both go through. */
    public Optional<Ticket> lock(JdbcClient j, UUID id) {
        return j.sql("SELECT " + COLUMNS + " FROM tickets WHERE ticket_id = :id FOR UPDATE").param("id", id).query(TicketRepository::map).optional();
    }

    public List<Ticket> byRequester(JdbcClient j, String requester) {
        return j.sql("SELECT " + COLUMNS + " FROM tickets WHERE requester = :r ORDER BY ticket_number DESC LIMIT 200").param("r", requester).query(TicketRepository::map).list();
    }

    public List<Ticket> all(JdbcClient j) {
        return j.sql("SELECT " + COLUMNS + " FROM tickets ORDER BY ticket_number DESC LIMIT 200").query(TicketRepository::map).list();
    }

    public List<Ticket> inState(JdbcClient j, TicketState state, String excludeRequester) {
        return j.sql("SELECT " + COLUMNS + " FROM tickets WHERE state = :s AND requester <> :r ORDER BY created_at LIMIT 200")
                .param("s", state.name()).param("r", excludeRequester).query(TicketRepository::map).list();
    }

    public void setState(JdbcClient j, UUID id, TicketState state, String environmentRef, Instant now) {
        j.sql("UPDATE tickets SET state = :s, environment_ref = COALESCE(:ref, environment_ref), updated_at = :now WHERE ticket_id = :id")
                .param("s", state.name()).param("ref", environmentRef).param("now", Timestamp.from(now)).param("id", id).update();
    }

    public void addEvent(JdbcClient j, UUID tenant, UUID ticket, Instant at, String actor, String action, TicketState from, TicketState to, String comment) {
        j.sql("INSERT INTO ticket_history (tenant_id, ticket_id, at, actor, action, from_state, to_state, comment) VALUES (:t, :ticket, :at, :actor, :action, :from, :to, :comment)")
                .param("t", tenant).param("ticket", ticket).param("at", Timestamp.from(at)).param("actor", actor).param("action", action)
                .param("from", from == null ? null : from.name()).param("to", to == null ? null : to.name()).param("comment", comment).update();
    }

    public List<Ticket.Event> history(JdbcClient j, UUID ticket) {
        return j.sql("SELECT event_id, at, actor, action, from_state, to_state, comment FROM ticket_history WHERE ticket_id = :t ORDER BY event_id")
                .param("t", ticket).query((r, i) -> new Ticket.Event(r.getLong("event_id"), r.getObject("at", OffsetDateTime.class).toInstant(), r.getString("actor"),
                        r.getString("action"), r.getString("from_state") == null ? null : TicketState.valueOf(r.getString("from_state")),
                        r.getString("to_state") == null ? null : TicketState.valueOf(r.getString("to_state")), r.getString("comment"))).list();
    }
}
