package com.motivitylabs.mls.itsmapi;

import com.platform.security.features.RequiresFeature;
import com.platform.security.features.TenantFeatures;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The ITSM Product's API. Each call needs the feature (key {@code itsm.<group>.<name>}) to be live for the caller's tenant, otherwise the
 * answer is 404 as if the call did not exist. Who the caller is and which tenant they belong to come from the verified token.
 */
@RestController
public class TicketsController {

    public record BuildView(String name, String sha256, long size) {
    }

    public record TicketView(UUID id, String reference, long number, String title, String description, String requester, String requesterName, String state,
                             String config, int ttlHours, BuildView build, String environmentRef, Instant createdAt, Instant updatedAt) {
        static TicketView of(Ticket t) {
            return new TicketView(t.id(), t.reference(), t.number(), t.title(), t.description(), t.requester(), t.requesterName(), t.state().name(), t.config(),
                    t.ttlHours(), new BuildView(t.buildName(), t.buildSha256(), t.buildSize()), t.environmentRef(), t.createdAt(), t.updatedAt());
        }
    }

    public record EventView(long id, Instant at, String actor, String action, String from, String to, String comment) {
        static EventView of(Ticket.Event e) {
            return new EventView(e.id(), e.at(), e.actor(), e.action(), e.from() == null ? null : e.from().name(), e.to() == null ? null : e.to().name(), e.comment());
        }
    }

    public record DetailView(TicketView ticket, List<EventView> history, boolean canDecide) {
    }

    public record Decision(String comment) {
    }

    public record Move(String to, String comment) {
    }

    private final TicketService tickets;
    private final ApproverDirectory approvers;
    private final TenantFeatures features;

    public TicketsController(TicketService tickets, ApproverDirectory approvers, TenantFeatures features) {
        this.tickets = tickets;
        this.approvers = approvers;
        this.features = features;
    }

    @GetMapping("/configurations")
    @RequiresFeature("itsm.tickets.tickets_create_provisioning")
    public List<String> configurations() {
        return tickets.configurations();
    }

    /** {@code scope=mine} (default) lists the caller's own tickets; {@code scope=all} lists the tenant's, for approvers, when the tenant has that feature. */
    @GetMapping("/tickets")
    @RequiresFeature("itsm.tickets.tickets_view_own")
    public List<TicketView> list(Authentication authentication, @RequestParam(defaultValue = "mine") String scope) {
        Actor actor = approvers.actor(authentication);
        if ("all".equals(scope)) {
            if (!features.on("itsm.tickets.tickets_view_all")) {
                throw new Errors.NotFound();
            }
            return tickets.all(actor).stream().map(TicketView::of).toList();
        }
        return tickets.mine(actor).stream().map(TicketView::of).toList();
    }

    @PostMapping(value = "/tickets", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequiresFeature({"itsm.tickets.tickets_create_provisioning", "itsm.tickets.tickets_attach"})
    public ResponseEntity<TicketView> raise(Authentication authentication, @RequestParam String title, @RequestParam(defaultValue = "") String description,
                                            @RequestParam String config, @RequestParam(required = false) Integer ttlHours,
                                            @RequestParam("build") MultipartFile build) throws IOException {
        Ticket t;
        try (var in = build.getInputStream()) {
            t = tickets.raise(approvers.actor(authentication), title, description, config, ttlHours, build.getOriginalFilename(), build.getContentType(), in);
        }
        return ResponseEntity.created(URI.create("/api/v1/tickets/" + t.id())).body(TicketView.of(t));
    }

    @GetMapping("/tickets/{id}")
    @RequiresFeature("itsm.tickets.tickets_view_own")
    public DetailView get(Authentication authentication, @PathVariable UUID id) {
        Actor actor = approvers.actor(authentication);
        TicketService.Detail d = tickets.view(actor, id);
        boolean canDecide = actor.approver() && !d.ticket().requester().equals(actor.subject()) && d.ticket().state() == TicketState.PENDING_APPROVAL;
        return new DetailView(TicketView.of(d.ticket()), d.history().stream().map(EventView::of).toList(), canDecide);
    }

    /** Checks on provisioning: asks for the environment of an approved ticket, or sees whether it is ready. */
    @PostMapping("/tickets/{id}/refresh")
    @RequiresFeature("itsm.tickets.tickets_view_own")
    public TicketView refresh(Authentication authentication, @PathVariable UUID id) {
        return TicketView.of(tickets.advance(approvers.actor(authentication), id));
    }

    @PostMapping("/tickets/{id}/transitions")
    @RequiresFeature("itsm.tickets.tickets_transition")
    public TicketView transition(Authentication authentication, @PathVariable UUID id, @RequestBody Move move) {
        TicketState to;
        try {
            to = TicketState.valueOf(move.to() == null ? "" : move.to());
        } catch (IllegalArgumentException e) {
            throw new Errors.BadRequest("Unknown state");
        }
        return TicketView.of(tickets.transition(approvers.actor(authentication), id, to, move.comment()));
    }

    @GetMapping("/approvals")
    @RequiresFeature("itsm.approvals.approvals_queue")
    public List<TicketView> queue(Authentication authentication) {
        return tickets.approvalQueue(approvers.actor(authentication)).stream().map(TicketView::of).toList();
    }

    @PostMapping("/tickets/{id}/approve")
    @RequiresFeature("itsm.approvals.approvals_decide")
    public TicketView approve(Authentication authentication, @PathVariable UUID id, @RequestBody(required = false) Decision decision) {
        return TicketView.of(tickets.approve(approvers.actor(authentication), id, decision == null ? null : decision.comment()));
    }

    @PostMapping("/tickets/{id}/reject")
    @RequiresFeature("itsm.approvals.approvals_decide")
    public TicketView reject(Authentication authentication, @PathVariable UUID id, @RequestBody(required = false) Decision decision) {
        return TicketView.of(tickets.reject(approvers.actor(authentication), id, decision == null ? null : decision.comment()));
    }
}
