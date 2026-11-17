package com.motivitylabs.mls.itsmapi;

import com.platform.security.tenancy.TenantContext;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.util.List;
import java.util.UUID;

/**
 * The ticket rules. All reads and writes are for the tenant of the current request (row-level security enforces it in the database as well).
 * The moves between states are the ones in {@link TicketState}; every move and every decision is written to the ticket's history.
 */
@Service
public class TicketService {

    public static final int DEFAULT_TTL_HOURS = 8;
    public static final int MAX_TTL_HOURS = 72;
    private static final String SYSTEM = "system";

    public record Detail(Ticket ticket, List<Ticket.Event> history) {
    }

    private final TenantDb db;
    private final TicketRepository repo;
    private final BuildIntake intake;
    private final BuildStore store;
    private final ProvisioningClient provisioning;
    private final ItsmProperties properties;
    private final Clock clock;

    public TicketService(TenantDb db, TicketRepository repo, BuildIntake intake, BuildStore store, ProvisioningClient provisioning, ItsmProperties properties, Clock clock) {
        this.db = db;
        this.repo = repo;
        this.intake = intake;
        this.store = store;
        this.provisioning = provisioning;
        this.properties = properties;
        this.clock = clock;
    }

    public List<String> configurations() {
        return properties.configurations();
    }

    // ---- raising

    public Ticket raise(Actor actor, String title, String description, String config, Integer ttlHours, String fileName, String contentType, InputStream build) {
        String cleanTitle = title == null ? "" : title.trim();
        if (cleanTitle.length() < 3 || cleanTitle.length() > 200) {
            throw new Errors.BadRequest("The title needs 3 to 200 characters");
        }
        String text = description == null ? "" : description.trim();
        if (text.length() > 8000) {
            throw new Errors.BadRequest("The description is longer than 8000 characters");
        }
        if (config == null || !properties.configurations().contains(config)) {
            throw new Errors.BadRequest("Choose one of the available configurations: " + String.join(", ", properties.configurations()));
        }
        int ttl = ttlHours == null ? DEFAULT_TTL_HOURS : ttlHours;
        if (ttl < 1 || ttl > MAX_TTL_HOURS) {
            throw new Errors.BadRequest("The lifetime must be 1 to " + MAX_TTL_HOURS + " hours; a longer one needs a new ticket");
        }
        java.util.UUID tenant = TenantContext.require();
        UUID id = UUID.randomUUID();
        BuildIntake.Accepted accepted;
        try {
            accepted = intake.accept(tenant, id, fileName, contentType, build);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        try {
            return db.inTenant(j -> {
                var now = clock.instant();
                long number = repo.nextNumber(j, tenant);
                Ticket ticket = new Ticket(id, tenant, number, cleanTitle, text, actor.subject(), actor.name(), TicketState.RAISED, config, ttl,
                        accepted.displayName(), accepted.stored().sha256(), accepted.stored().size(), null, now, now);
                repo.insert(j, ticket, accepted.stored().key());
                repo.addEvent(j, tenant, id, now, actor.subject(), "raised", null, TicketState.RAISED,
                        "Build " + accepted.displayName() + " (" + accepted.stored().size() + " bytes, sha256 " + accepted.stored().sha256() + ")");
                repo.setState(j, id, TicketState.PENDING_APPROVAL, null, now);
                repo.addEvent(j, tenant, id, now, actor.subject(), "submitted", TicketState.RAISED, TicketState.PENDING_APPROVAL, null);
                return repo.find(j, id).orElseThrow();
            });
        } catch (RuntimeException e) {
            store.delete(accepted.stored().key());
            throw e;
        }
    }

    // ---- reading

    /** A requester sees their own tickets; an approver sees every ticket of the tenant. Anyone else gets "no such ticket". */
    public Detail view(Actor actor, UUID id) {
        return db.inTenant(j -> {
            Ticket t = repo.find(j, id).orElseThrow(Errors.NotFound::new);
            requireVisible(actor, t);
            return new Detail(t, repo.history(j, id));
        });
    }

    public List<Ticket> mine(Actor actor) {
        return db.inTenant(j -> repo.byRequester(j, actor.subject()));
    }

    public List<Ticket> all(Actor actor) {
        if (!actor.approver()) {
            throw new Errors.Forbidden("Only approvers can see every ticket");
        }
        return db.inTenant(repo::all);
    }

    /** Tickets waiting for a decision, other than the caller's own (they cannot decide those). */
    public List<Ticket> approvalQueue(Actor actor) {
        if (!actor.approver()) {
            throw new Errors.Forbidden("Only approvers can see the approval queue");
        }
        return db.inTenant(j -> repo.inState(j, TicketState.PENDING_APPROVAL, actor.subject()));
    }

    // ---- deciding

    public Ticket approve(Actor actor, UUID id, String comment) {
        decide(actor, id, true, comment);
        return advance(actor, id);
    }

    public Ticket reject(Actor actor, UUID id, String comment) {
        if (comment == null || comment.isBlank()) {
            throw new Errors.BadRequest("Say why the ticket is rejected");
        }
        decide(actor, id, false, comment);
        return view(actor, id).ticket();
    }

    private void decide(Actor actor, UUID id, boolean approve, String comment) {
        String note = comment == null || comment.isBlank() ? null : comment.trim();
        if (note != null && note.length() > 2000) {
            throw new Errors.BadRequest("The comment is longer than 2000 characters");
        }
        db.inTenant(j -> {
            Ticket t = repo.lock(j, id).orElseThrow(Errors.NotFound::new);
            if (!actor.approver()) {
                throw new Errors.Forbidden("Only approvers can decide tickets");
            }
            if (t.requester().equals(actor.subject())) {
                throw new Errors.Forbidden("You cannot decide your own ticket");
            }
            TicketState to = approve ? TicketState.APPROVED : TicketState.REJECTED;
            if (!t.state().canMoveTo(to)) {
                throw new TicketState.IllegalTransitionException(t.state(), to);
            }
            var now = clock.instant();
            repo.setState(j, id, to, null, now);
            repo.addEvent(j, t.tenantId(), id, now, actor.subject(), approve ? "approved" : "rejected", t.state(), to, note);
            return null;
        });
    }

    // ---- moving on

    /** The moves a person may ask for: testing, reported, closed. Approval, rejection and provisioning have their own calls. */
    public Ticket transition(Actor actor, UUID id, TicketState to, String comment) {
        return db.inTenant(j -> {
            Ticket t = repo.lock(j, id).orElseThrow(Errors.NotFound::new);
            requireVisible(actor, t);
            if (!TicketState.MANUAL_TARGETS.contains(to) || !t.state().canMoveTo(to)) {
                throw new TicketState.IllegalTransitionException(t.state(), to);
            }
            var now = clock.instant();
            repo.setState(j, id, to, null, now);
            repo.addEvent(j, t.tenantId(), id, now, actor.subject(), "transition", t.state(), to, comment == null || comment.isBlank() ? null : comment.trim());
            return repo.find(j, id).orElseThrow();
        });
    }

    /**
     * Pushes an approved ticket on: asks for the environment (Approved to Provisioning), or checks whether it is ready (Provisioning to Active).
     * Safe to call again: provisioning is idempotent on the ticket id, and a ticket in any other state is returned as it is.
     */
    public Ticket advance(Actor actor, UUID id) {
        Ticket t = view(actor, id).ticket();
        if (t.state() == TicketState.APPROVED) {
            String ref;
            try {
                ref = provisioning.request(new ProvisioningClient.Request(t.id(), t.tenantId(), t.reference(), t.requester(), t.config(), t.ttlHours(), t.buildSha256()));
            } catch (RuntimeException e) {
                recordSystem(t, "provisioning-failed", t.state(), t.state(), "Provisioning could not be requested; it will be tried again");
                return t;
            }
            return move(t, TicketState.APPROVED, TicketState.PROVISIONING, ref, "provisioning-requested", "Environment requested: " + ref);
        }
        if (t.state() == TicketState.PROVISIONING && t.environmentRef() != null) {
            ProvisioningClient.Status status;
            try {
                status = provisioning.status(t.environmentRef());
            } catch (RuntimeException e) {
                return t;
            }
            if (status.state() == ProvisioningClient.State.READY) {
                return move(t, TicketState.PROVISIONING, TicketState.ACTIVE, null, "provisioned", status.detail());
            }
            if (status.state() == ProvisioningClient.State.FAILED) {
                recordSystem(t, "provisioning-failed", t.state(), t.state(), status.detail());
            }
        }
        return t;
    }

    private Ticket move(Ticket t, TicketState from, TicketState to, String ref, String action, String note) {
        return db.inTenant(j -> {
            Ticket now = repo.lock(j, t.id()).orElseThrow(Errors.NotFound::new);
            if (now.state() != from) {
                return now;   // someone else already moved it
            }
            var at = clock.instant();
            repo.setState(j, t.id(), to, ref, at);
            repo.addEvent(j, t.tenantId(), t.id(), at, SYSTEM, action, from, to, note);
            return repo.find(j, t.id()).orElseThrow();
        });
    }

    private void recordSystem(Ticket t, String action, TicketState from, TicketState to, String note) {
        db.inTenant(j -> {
            repo.addEvent(j, t.tenantId(), t.id(), clock.instant(), SYSTEM, action, from, to, note);
            return null;
        });
    }

    private static void requireVisible(Actor actor, Ticket t) {
        if (!actor.approver() && !t.requester().equals(actor.subject())) {
            throw new Errors.NotFound();
        }
    }
}
