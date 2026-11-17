package com.motivitylabs.mls.itsmapi;

import com.platform.security.tenancy.TenantContext;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

import javax.sql.DataSource;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The ticket rules against a real PostgreSQL, with row-level security as the (non-super) owner would meet it. */
@EnabledIfEnvironmentVariable(named = "TEST_PG_URL", matches = ".+")
class TicketServicePostgresTest {

    private static final UUID TENANT_A = UUID.randomUUID();
    private static final UUID TENANT_B = UUID.randomUUID();
    private static final Actor ALICE = new Actor("alice", "Alice", false);
    private static final Actor BOB = new Actor("bob", "Bob", false);
    private static final Actor ROSA = new Actor("rosa", "Rosa (approver)", true);
    private static final Actor ALICE_AS_APPROVER = new Actor("alice", "Alice", true);

    private static DataSource ds;

    @TempDir
    static Path storage;

    private final List<ProvisioningClient.Request> requests = new ArrayList<>();
    private Supplier<ProvisioningClient.Status> status = () -> new ProvisioningClient.Status(ProvisioningClient.State.PENDING, "starting");
    private boolean provisioningDown;
    private TicketService service;

    @BeforeAll
    static void migrate() throws Exception {
        ds = TestDb.create("itsm_service_test");
    }

    @BeforeEach
    void wire() {
        requests.clear();
        provisioningDown = false;
        status = () -> new ProvisioningClient.Status(ProvisioningClient.State.PENDING, "starting");
        ProvisioningClient client = new ProvisioningClient() {
            public String request(Request request) {
                if (provisioningDown) {
                    throw new IllegalStateException("provisioning API unreachable");
                }
                requests.add(request);
                return "env-" + request.idempotencyKey();
            }

            public Status status(String reference) {
                return status.get();
            }
        };
        ItsmProperties props = new ItsmProperties(List.of("ubuntu-22.04-postgres-16"), List.of(), storage.toString(), 10_000_000, List.of());
        BuildStore store = new LocalDiskBuildStore(storage);
        service = new TicketService(new TenantDb(ds, new DataSourceTransactionManager(ds)), new TicketRepository(), new BuildIntake(store, props), store, client, props, Clock.systemUTC());
    }

    private static <T> T as(UUID tenant, Supplier<T> work) {
        return TenantContext.callAs(tenant, work);
    }

    private Ticket raise(UUID tenant, Actor who) {
        return as(tenant, () -> {
            try {
                return service.raise(who, "Test the plugin on Ubuntu 22.04", "Please", "ubuntu-22.04-postgres-16", 8, "build.zip", "application/zip", new ByteArrayInputStream(BuildIntakeTest.zip(100)));
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
    }

    // ---- raising and numbering

    @Test
    void aRaisedTicketWaitsForApprovalAndItsHistoryShowsHow() {
        Ticket t = raise(TENANT_A, ALICE);
        assertThat(t.state()).isEqualTo(TicketState.PENDING_APPROVAL);
        assertThat(t.reference()).startsWith("TKT-");
        assertThat(t.requester()).isEqualTo("alice");
        assertThat(t.buildSha256()).hasSize(64);
        List<Ticket.Event> history = as(TENANT_A, () -> service.view(ALICE, t.id()).history());
        assertThat(history).extracting(Ticket.Event::action).containsExactly("raised", "submitted");
        assertThat(history.get(0).comment()).contains(t.buildSha256());
        assertThat(history.get(1).from()).isEqualTo(TicketState.RAISED);
        assertThat(history.get(1).to()).isEqualTo(TicketState.PENDING_APPROVAL);
    }

    @Test
    void numbersCountUpForEachTenantSeparately() {
        UUID c1 = UUID.randomUUID();
        UUID c2 = UUID.randomUUID();
        assertThat(raise(c1, ALICE).number()).isEqualTo(1001);
        assertThat(raise(c1, ALICE).number()).isEqualTo(1002);
        assertThat(raise(c2, ALICE).number()).isEqualTo(1001);
    }

    @Test
    void theBuildIsStoredUnderTheTenantAndTicketAndItsChecksumIsRecorded() throws Exception {
        Ticket t = raise(TENANT_A, ALICE);
        Path file = storage.resolve(t.tenantId() + "/" + t.id() + "/build.zip");
        assertThat(Files.size(file)).isEqualTo(t.buildSize());
    }

    @Test
    void aBadRequestCreatesNoTicketAndLeavesNoFile() throws Exception {
        long before = as(TENANT_A, () -> (long) service.mine(ALICE).size());
        for (Runnable bad : List.<Runnable>of(
                () -> raiseWith("ab", "ubuntu-22.04-postgres-16", 8, "application/zip", validZip()),
                () -> raiseWith("A fine title", "windows-95", 8, "application/zip", validZip()),
                () -> raiseWith("A fine title", "ubuntu-22.04-postgres-16", 0, "application/zip", validZip()),
                () -> raiseWith("A fine title", "ubuntu-22.04-postgres-16", 73, "application/zip", validZip()),
                () -> raiseWith("A fine title", "ubuntu-22.04-postgres-16", 8, "text/plain", validZip()),
                () -> raiseWith("A fine title", "ubuntu-22.04-postgres-16", 8, "application/zip", "not a zip".getBytes()))) {
            assertThatThrownBy(bad::run).isInstanceOf(Errors.BadRequest.class);
        }
        assertThat(as(TENANT_A, () -> (long) service.mine(ALICE).size())).isEqualTo(before);
    }

    private static byte[] validZip() {
        try {
            return BuildIntakeTest.zip(50);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private void raiseWith(String title, String config, int ttl, String type, byte[] data) {
        as(TENANT_A, () -> service.raise(ALICE, title, "", config, ttl, "b.zip", type, new ByteArrayInputStream(data)));
    }

    // ---- tenants cannot see each other

    @Test
    void oneTenantNeverSeesAnotherTenantsTickets() {
        Ticket t = raise(TENANT_A, ALICE);
        assertThatThrownBy(() -> as(TENANT_B, () -> service.view(ALICE, t.id()))).isInstanceOf(Errors.NotFound.class);
        assertThatThrownBy(() -> as(TENANT_B, () -> service.view(ROSA, t.id()))).isInstanceOf(Errors.NotFound.class);
        assertThat(as(TENANT_B, () -> service.mine(ALICE)).stream().map(Ticket::id)).doesNotContain(t.id());
        assertThat(as(TENANT_B, () -> service.all(ROSA)).stream().map(Ticket::id)).doesNotContain(t.id());
        assertThatThrownBy(() -> as(TENANT_B, () -> service.approve(ROSA, t.id(), "ok"))).isInstanceOf(Errors.NotFound.class);
    }

    @Test
    void withoutATenantTheDatabaseReturnsNoRowsAtAll() {
        raise(TENANT_A, ALICE);
        JdbcClient plain = JdbcClient.create(ds);
        assertThat(plain.sql("SELECT count(*) FROM tickets").query(Long.class).single()).isZero();
        assertThat(plain.sql("SELECT count(*) FROM ticket_history").query(Long.class).single()).isZero();
        assertThat(plain.sql("SELECT count(*) FROM ticket_counters").query(Long.class).single()).isZero();
    }

    @Test
    void theDatabaseRefusesToWriteARowForAnotherTenant() {
        Ticket t = raise(TENANT_A, ALICE);
        TenantDb db = new TenantDb(ds, new DataSourceTransactionManager(ds));
        assertThatThrownBy(() -> as(TENANT_B, () -> db.inTenant(j -> j.sql("INSERT INTO ticket_history (tenant_id, ticket_id, actor, action) VALUES (:t, :ticket, 'x', 'forged')")
                .param("t", TENANT_A).param("ticket", t.id()).update()))).rootCause().hasMessageContaining("row-level security");
    }

    @Test
    void theTenantCannotBeNamedByTheRequestOnlyByTheContext() {
        assertThatThrownBy(() -> service.mine(ALICE)).isInstanceOf(IllegalStateException.class).hasMessageContaining("No tenant context");
    }

    // ---- who may see what

    @Test
    void aRequesterSeesOnlyTheirOwnTicketsAndAnApproverSeesAll() {
        Ticket t = raise(TENANT_A, ALICE);
        assertThat(as(TENANT_A, () -> service.mine(ALICE)).stream().map(Ticket::id)).contains(t.id());
        assertThat(as(TENANT_A, () -> service.mine(BOB)).stream().map(Ticket::id)).doesNotContain(t.id());
        assertThatThrownBy(() -> as(TENANT_A, () -> service.view(BOB, t.id()))).isInstanceOf(Errors.NotFound.class);
        assertThat(as(TENANT_A, () -> service.view(ROSA, t.id())).ticket().id()).isEqualTo(t.id());
        assertThat(as(TENANT_A, () -> service.all(ROSA)).stream().map(Ticket::id)).contains(t.id());
        assertThatThrownBy(() -> as(TENANT_A, () -> service.all(ALICE))).isInstanceOf(Errors.Forbidden.class);
    }

    @Test
    void theApprovalQueueHoldsOtherPeoplesPendingTicketsOnly() {
        Ticket mine = raise(TENANT_A, ALICE);
        Ticket others = raise(TENANT_A, BOB);
        List<UUID> queue = as(TENANT_A, () -> service.approvalQueue(ALICE_AS_APPROVER)).stream().map(Ticket::id).toList();
        assertThat(queue).contains(others.id()).doesNotContain(mine.id());
        assertThatThrownBy(() -> as(TENANT_A, () -> service.approvalQueue(BOB))).isInstanceOf(Errors.Forbidden.class);
    }

    // ---- deciding

    @Test
    void approvingRecordsWhoWhenAndWhyThenRequestsTheEnvironment() {
        Ticket t = raise(TENANT_A, ALICE);
        Ticket after = as(TENANT_A, () -> service.approve(ROSA, t.id(), "Looks fine"));
        assertThat(after.state()).isEqualTo(TicketState.PROVISIONING);
        assertThat(after.environmentRef()).isEqualTo("env-" + t.id());
        assertThat(requests).hasSize(1);
        ProvisioningClient.Request r = requests.get(0);
        assertThat(r.idempotencyKey()).isEqualTo(t.id());
        assertThat(r.owner()).isEqualTo("alice");
        assertThat(r.ticket()).isEqualTo(t.reference());
        assertThat(r.config()).isEqualTo("ubuntu-22.04-postgres-16");
        assertThat(r.ttlHours()).isEqualTo(8);
        List<Ticket.Event> history = as(TENANT_A, () -> service.view(ALICE, t.id()).history());
        assertThat(history).extracting(Ticket.Event::action).containsExactly("raised", "submitted", "approved", "provisioning-requested");
        Ticket.Event decision = history.get(2);
        assertThat(decision.actor()).isEqualTo("rosa");
        assertThat(decision.comment()).isEqualTo("Looks fine");
        assertThat(decision.at()).isNotNull();
        assertThat(decision.from()).isEqualTo(TicketState.PENDING_APPROVAL);
        assertThat(decision.to()).isEqualTo(TicketState.APPROVED);
    }

    @Test
    void aRequesterCanNeverDecideTheirOwnTicketEvenAsAnApprover() {
        Ticket t = raise(TENANT_A, ALICE);
        assertThatThrownBy(() -> as(TENANT_A, () -> service.approve(ALICE_AS_APPROVER, t.id(), "me"))).isInstanceOf(Errors.Forbidden.class).hasMessageContaining("your own");
        assertThatThrownBy(() -> as(TENANT_A, () -> service.reject(ALICE_AS_APPROVER, t.id(), "me"))).isInstanceOf(Errors.Forbidden.class);
        assertThat(as(TENANT_A, () -> service.view(ALICE, t.id())).ticket().state()).isEqualTo(TicketState.PENDING_APPROVAL);
        assertThat(requests).isEmpty();
    }

    @Test
    void aPersonWhoIsNotAnApproverCannotDecide() {
        Ticket t = raise(TENANT_A, ALICE);
        assertThatThrownBy(() -> as(TENANT_A, () -> service.approve(BOB, t.id(), "sure"))).isInstanceOf(Errors.Forbidden.class);
        assertThatThrownBy(() -> as(TENANT_A, () -> service.reject(BOB, t.id(), "no"))).isInstanceOf(Errors.Forbidden.class);
        assertThat(as(TENANT_A, () -> service.view(ALICE, t.id())).ticket().state()).isEqualTo(TicketState.PENDING_APPROVAL);
    }

    @Test
    void aRejectionNeedsAReasonThenTheTicketCanBeClosed() {
        Ticket t = raise(TENANT_A, ALICE);
        assertThatThrownBy(() -> as(TENANT_A, () -> service.reject(ROSA, t.id(), "  "))).isInstanceOf(Errors.BadRequest.class);
        assertThat(as(TENANT_A, () -> service.reject(ROSA, t.id(), "Not this quarter")).state()).isEqualTo(TicketState.REJECTED);
        Ticket.Event e = as(TENANT_A, () -> service.view(ALICE, t.id())).history().stream().filter(x -> x.action().equals("rejected")).findFirst().orElseThrow();
        assertThat(e.actor()).isEqualTo("rosa");
        assertThat(e.comment()).isEqualTo("Not this quarter");
        assertThat(requests).isEmpty();
        assertThat(as(TENANT_A, () -> service.transition(ALICE, t.id(), TicketState.CLOSED, null)).state()).isEqualTo(TicketState.CLOSED);
    }

    @Test
    void aTicketCannotBeDecidedTwice() {
        Ticket t = raise(TENANT_A, ALICE);
        as(TENANT_A, () -> service.approve(ROSA, t.id(), "ok"));
        assertThatThrownBy(() -> as(TENANT_A, () -> service.approve(ROSA, t.id(), "again"))).isInstanceOf(TicketState.IllegalTransitionException.class);
        assertThatThrownBy(() -> as(TENANT_A, () -> service.reject(ROSA, t.id(), "changed my mind"))).isInstanceOf(TicketState.IllegalTransitionException.class);
        assertThat(requests).hasSize(1);
    }

    // ---- provisioning through the interface

    @Test
    void ifProvisioningCannotBeReachedTheTicketStaysApprovedAndTriesAgainOnRefresh() {
        Ticket t = raise(TENANT_A, ALICE);
        provisioningDown = true;
        assertThat(as(TENANT_A, () -> service.approve(ROSA, t.id(), "ok")).state()).isEqualTo(TicketState.APPROVED);
        assertThat(as(TENANT_A, () -> service.view(ALICE, t.id())).history()).extracting(Ticket.Event::action).contains("provisioning-failed");
        provisioningDown = false;
        assertThat(as(TENANT_A, () -> service.advance(ALICE, t.id())).state()).isEqualTo(TicketState.PROVISIONING);
        assertThat(requests).hasSize(1);
    }

    @Test
    void refreshingMovesAReadyEnvironmentToActiveAndLeavesAPendingOneAlone() {
        Ticket t = raise(TENANT_A, ALICE);
        as(TENANT_A, () -> service.approve(ROSA, t.id(), "ok"));
        assertThat(as(TENANT_A, () -> service.advance(ALICE, t.id())).state()).isEqualTo(TicketState.PROVISIONING);
        status = () -> new ProvisioningClient.Status(ProvisioningClient.State.READY, "ready at 10.0.0.5");
        assertThat(as(TENANT_A, () -> service.advance(ALICE, t.id())).state()).isEqualTo(TicketState.ACTIVE);
        assertThat(as(TENANT_A, () -> service.advance(ALICE, t.id())).state()).isEqualTo(TicketState.ACTIVE);
        assertThat(as(TENANT_A, () -> service.view(ALICE, t.id())).history()).extracting(Ticket.Event::action).endsWith("provisioned");
    }

    @Test
    void aFailedEnvironmentIsRecordedAndTheTicketStaysWhereItIs() {
        Ticket t = raise(TENANT_A, ALICE);
        as(TENANT_A, () -> service.approve(ROSA, t.id(), "ok"));
        status = () -> new ProvisioningClient.Status(ProvisioningClient.State.FAILED, "no capacity");
        assertThat(as(TENANT_A, () -> service.advance(ALICE, t.id())).state()).isEqualTo(TicketState.PROVISIONING);
        Ticket.Event last = as(TENANT_A, () -> service.view(ALICE, t.id())).history().getLast();
        assertThat(last.action()).isEqualTo("provisioning-failed");
        assertThat(last.comment()).isEqualTo("no capacity");
    }

    // ---- moving on by hand

    @Test
    void theRestOfTheLifeIsMovedByHandInOrder() {
        Ticket t = raise(TENANT_A, ALICE);
        as(TENANT_A, () -> service.approve(ROSA, t.id(), "ok"));
        status = () -> new ProvisioningClient.Status(ProvisioningClient.State.READY, "ready");
        as(TENANT_A, () -> service.advance(ALICE, t.id()));
        assertThatThrownBy(() -> as(TENANT_A, () -> service.transition(ALICE, t.id(), TicketState.CLOSED, null))).isInstanceOf(TicketState.IllegalTransitionException.class);
        assertThat(as(TENANT_A, () -> service.transition(ALICE, t.id(), TicketState.TESTING, "Started")).state()).isEqualTo(TicketState.TESTING);
        assertThat(as(TENANT_A, () -> service.transition(ALICE, t.id(), TicketState.REPORTED, "Report attached")).state()).isEqualTo(TicketState.REPORTED);
        assertThat(as(TENANT_A, () -> service.transition(ALICE, t.id(), TicketState.CLOSED, null)).state()).isEqualTo(TicketState.CLOSED);
        assertThatThrownBy(() -> as(TENANT_A, () -> service.transition(ALICE, t.id(), TicketState.TESTING, null))).isInstanceOf(TicketState.IllegalTransitionException.class);
    }

    @Test
    void approvalAndProvisioningCannotBeFakedThroughTheManualMove() {
        Ticket t = raise(TENANT_A, ALICE);
        for (TicketState target : Stream.of(TicketState.APPROVED, TicketState.PROVISIONING, TicketState.ACTIVE, TicketState.REJECTED, TicketState.PENDING_APPROVAL).toList()) {
            assertThatThrownBy(() -> as(TENANT_A, () -> service.transition(ALICE, t.id(), target, "please"))).isInstanceOf(TicketState.IllegalTransitionException.class);
        }
        assertThat(as(TENANT_A, () -> service.view(ALICE, t.id())).ticket().state()).isEqualTo(TicketState.PENDING_APPROVAL);
    }

    @Test
    void someoneElseCannotMoveATicketTheyCannotSee() {
        Ticket t = raise(TENANT_A, ALICE);
        assertThatThrownBy(() -> as(TENANT_A, () -> service.transition(BOB, t.id(), TicketState.CLOSED, null))).isInstanceOf(Errors.NotFound.class);
    }

    // ---- history

    @Test
    void historyCanNeverBeChangedOrRemoved() {
        Ticket t = raise(TENANT_A, ALICE);
        TenantDb db = new TenantDb(ds, new DataSourceTransactionManager(ds));
        assertThatThrownBy(() -> as(TENANT_A, () -> db.inTenant(j -> j.sql("UPDATE ticket_history SET actor = 'mallory' WHERE ticket_id = :t").param("t", t.id()).update())))
                .hasMessageContaining("append-only");
        assertThatThrownBy(() -> as(TENANT_A, () -> db.inTenant(j -> j.sql("DELETE FROM ticket_history WHERE ticket_id = :t").param("t", t.id()).update())))
                .hasMessageContaining("append-only");
    }
}
