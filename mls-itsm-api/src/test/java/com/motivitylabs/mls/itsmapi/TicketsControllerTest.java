package com.motivitylabs.mls.itsmapi;

import com.platform.security.authz.RemotePermissionChecker;
import com.platform.security.features.FeatureGuardInterceptor;
import com.platform.security.features.TenantFeatures;
import com.platform.security.jwt.PlatformAuthentication;
import com.platform.security.jwt.PlatformPrincipal;
import com.platform.security.tenancy.TenantContext;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The HTTP layer: which feature each call needs (404 when it is off), who the caller is taken to be, and what each failure answers.
 * The ticket rules themselves are tested against a real database in {@link TicketServicePostgresTest}.
 */
class TicketsControllerTest {

    static final List<String> ALL = List.of("itsm.tickets.tickets_view_own", "itsm.tickets.tickets_view_all", "itsm.tickets.tickets_create_provisioning",
            "itsm.tickets.tickets_attach", "itsm.tickets.tickets_transition", "itsm.approvals.approvals_queue", "itsm.approvals.approvals_decide");

    static final UUID FULL = UUID.randomUUID();
    static final UUID VIEW_ONLY = UUID.randomUUID();
    static final UUID NONE = UUID.randomUUID();

    private static HttpServer tenants;

    @BeforeAll
    static void start() throws Exception {
        tenants = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        tenants.createContext("/internal/tenants/" + FULL + "/features", ex -> reply(ex, features(ALL)));
        tenants.createContext("/internal/tenants/" + VIEW_ONLY + "/features", ex -> reply(ex, features(List.of("itsm.tickets.tickets_view_own"))));
        tenants.createContext("/internal/tenants/" + NONE + "/features", ex -> reply(ex, features(List.of())));
        tenants.start();
    }

    @AfterAll
    static void stop() {
        tenants.stop(0);
    }

    private static String features(List<String> live) {
        List<String> rows = new ArrayList<>();
        for (String key : ALL) {
            boolean on = live.contains(key);
            rows.add("{\"key\":\"" + key + "\",\"product\":\"itsm\",\"entitled\":" + on + ",\"enabled\":true,\"effective\":" + on + "}");
        }
        return "[" + String.join(",", rows) + "]";
    }

    private static void reply(com.sun.net.httpserver.HttpExchange ex, String body) throws java.io.IOException {
        byte[] b = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json");
        ex.sendResponseHeaders(200, b.length);
        ex.getResponseBody().write(b);
        ex.close();
    }

    private final TicketService service = mock(TicketService.class);

    @SuppressWarnings("unchecked")
    private MockMvc mvc() {
        TenantFeatures features = new TenantFeatures("http://127.0.0.1:" + tenants.getAddress().getPort(), Duration.ofSeconds(5));
        ObjectProvider<RemotePermissionChecker> none = mock(ObjectProvider.class);
        ApproverDirectory approvers = new ApproverDirectory(new ItsmProperties(List.of("c"), List.of("rosa"), "x", 1, List.of()), none);
        return MockMvcBuilders.standaloneSetup(new TicketsController(service, approvers, features))
                .addInterceptors(new FeatureGuardInterceptor(features)).setControllerAdvice(new Errors.Handler()).build();
    }

    private static RequestPostProcessor user(String subject) {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "none").subject(subject).claim("preferred_username", subject).build();
        return request -> {
            request.setUserPrincipal(new PlatformAuthentication(new PlatformPrincipal(UUID.randomUUID(), subject, "http://issuer"), jwt, List.of()));
            return request;
        };
    }

    private static <T> T in(UUID tenant, Callable<T> work) {
        return TenantContext.callAs(tenant, () -> {
            try {
                return work.call();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
    }

    private static Ticket ticket(String state) {
        Instant now = Instant.parse("2026-11-17T10:00:00Z");
        return new Ticket(UUID.randomUUID(), FULL, 1001, "Test it", "", "alice", "Alice", TicketState.valueOf(state), "c", 8, "b.zip", "a".repeat(64), 10, null, now, now);
    }

    // ---- 404 when the feature is off

    @Test
    void everyCallIsAnsweredWith404WhenTheTenantHasNoItsmFeatures() {
        in(NONE, () -> {
            MockMvc m = mvc();
            String id = UUID.randomUUID().toString();
            for (var request : List.of(get("/tickets"), get("/tickets/" + id), get("/configurations"), get("/approvals"),
                    post("/tickets/" + id + "/approve").contentType(MediaType.APPLICATION_JSON).content("{}"),
                    post("/tickets/" + id + "/reject").contentType(MediaType.APPLICATION_JSON).content("{}"),
                    post("/tickets/" + id + "/transitions").contentType(MediaType.APPLICATION_JSON).content("{\"to\":\"TESTING\"}"),
                    post("/tickets/" + id + "/refresh"))) {
                try {
                    m.perform(request.with(user("alice"))).andExpect(status().isNotFound());
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            }
            try {
                m.perform(multipart("/tickets").file(new MockMultipartFile("build", "b.zip", "application/zip", new byte[] {'P', 'K', 3, 4})).param("title", "Hello").param("config", "c").with(user("alice")))
                        .andExpect(status().isNotFound());
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
            return null;
        });
        verifyNoInteractions(service);
    }

    @Test
    void aTenantWithOnlyViewingCanListButNotRaiseApproveOrMove() throws Exception {
        when(service.mine(any())).thenReturn(List.of(ticket("PENDING_APPROVAL")));
        in(VIEW_ONLY, () -> {
            MockMvc m = mvc();
            try {
                m.perform(get("/tickets").with(user("alice"))).andExpect(status().isOk()).andExpect(jsonPath("$[0].reference").value("TKT-1001"));
                m.perform(multipart("/tickets").file(new MockMultipartFile("build", "b.zip", "application/zip", new byte[] {'P', 'K', 3, 4})).param("title", "Hello").param("config", "c").with(user("alice")))
                        .andExpect(status().isNotFound());
                m.perform(post("/tickets/" + UUID.randomUUID() + "/approve").with(user("rosa"))).andExpect(status().isNotFound());
                m.perform(get("/approvals").with(user("rosa"))).andExpect(status().isNotFound());
                m.perform(get("/tickets").param("scope", "all").with(user("rosa"))).andExpect(status().isNotFound());
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
            return null;
        });
    }

    // ---- what the calls do when the features are on

    @Test
    void aTicketIsRaisedFromAMultipartFormAndTheBuildIsPassedOn() throws Exception {
        Ticket t = ticket("PENDING_APPROVAL");
        when(service.raise(any(), eq("Test it"), eq("Please"), eq("c"), eq(8), eq("b.zip"), eq("application/zip"), any())).thenReturn(t);
        in(FULL, () -> {
            try {
                mvc().perform(multipart("/tickets").file(new MockMultipartFile("build", "b.zip", "application/zip", new byte[] {'P', 'K', 3, 4, 1}))
                                .param("title", "Test it").param("description", "Please").param("config", "c").param("ttlHours", "8").with(user("alice")))
                        .andExpect(status().isCreated()).andExpect(jsonPath("$.id").value(t.id().toString())).andExpect(jsonPath("$.state").value("PENDING_APPROVAL"))
                        .andExpect(jsonPath("$.build.sha256").value("a".repeat(64)));
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
            return null;
        });
    }

    @Test
    void theCallerIsKnownFromTheTokenAndApproverStatusFromTheDirectoryNotTheRequest() throws Exception {
        Ticket t = ticket("PROVISIONING");
        when(service.approve(any(), eq(t.id()), any())).thenReturn(t);
        in(FULL, () -> {
            try {
                mvc().perform(post("/tickets/" + t.id() + "/approve").contentType(MediaType.APPLICATION_JSON).content("{\"comment\":\"Looks fine\"}").header("X-Approver", "true").with(user("rosa")))
                        .andExpect(status().isOk()).andExpect(jsonPath("$.state").value("PROVISIONING"));
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
            return null;
        });
        org.mockito.ArgumentCaptor<Actor> actor = org.mockito.ArgumentCaptor.forClass(Actor.class);
        verify(service).approve(actor.capture(), eq(t.id()), eq("Looks fine"));
        org.assertj.core.api.Assertions.assertThat(actor.getValue()).isEqualTo(new Actor("rosa", "rosa", true));
        in(FULL, () -> {
            try {
                mvc().perform(post("/tickets/" + t.id() + "/approve").contentType(MediaType.APPLICATION_JSON).content("{}").header("X-Approver", "true").with(user("mallory")));
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
            return null;
        });
        org.mockito.ArgumentCaptor<Actor> other = org.mockito.ArgumentCaptor.forClass(Actor.class);
        verify(service).approve(other.capture(), eq(t.id()), eq(null));
        org.assertj.core.api.Assertions.assertThat(other.getValue().approver()).isFalse();
    }

    @Test
    void theDetailSaysWhetherTheCallerCanDecide() throws Exception {
        Ticket t = ticket("PENDING_APPROVAL");
        when(service.view(any(), eq(t.id()))).thenReturn(new TicketService.Detail(t, List.of(new Ticket.Event(1, t.createdAt(), "alice", "raised", null, TicketState.RAISED, "x"))));
        in(FULL, () -> {
            try {
                mvc().perform(get("/tickets/" + t.id()).with(user("rosa"))).andExpect(status().isOk()).andExpect(jsonPath("$.canDecide").value(true))
                        .andExpect(jsonPath("$.history[0].action").value("raised"));
                mvc().perform(get("/tickets/" + t.id()).with(user("alice"))).andExpect(status().isOk()).andExpect(jsonPath("$.canDecide").value(false));
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
            return null;
        });
    }

    @Test
    void failuresAreAnsweredWithTheRightStatus() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.approve(any(), eq(id), any())).thenThrow(new Errors.Forbidden("You cannot decide your own ticket"));
        when(service.reject(any(), eq(id), any())).thenThrow(new TicketState.IllegalTransitionException(TicketState.APPROVED, TicketState.REJECTED));
        when(service.view(any(), eq(id))).thenThrow(new Errors.NotFound());
        when(service.raise(any(), any(), any(), any(), any(), any(), any(), any())).thenThrow(new Errors.TooLarge(100L * 1024 * 1024));
        in(FULL, () -> {
            try {
                MockMvc m = mvc();
                m.perform(post("/tickets/" + id + "/approve").with(user("alice"))).andExpect(status().isForbidden());
                m.perform(post("/tickets/" + id + "/reject").with(user("rosa"))).andExpect(status().isConflict()).andExpect(jsonPath("$.detail").value("A ticket cannot move from APPROVED to REJECTED"));
                m.perform(get("/tickets/" + id).with(user("alice"))).andExpect(status().isNotFound());
                m.perform(multipart("/tickets").file(new MockMultipartFile("build", "b.zip", "application/zip", new byte[] {'P', 'K', 3, 4})).param("title", "Hello").param("config", "c").with(user("alice")))
                        .andExpect(status().isPayloadTooLarge());
                m.perform(post("/tickets/" + id + "/transitions").contentType(MediaType.APPLICATION_JSON).content("{\"to\":\"NONSENSE\"}").with(user("alice"))).andExpect(status().isBadRequest());
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
            return null;
        });
        verify(service, never()).transition(any(), any(), any(), any());
    }

    @Test
    void everyTenantTicketsAreOnlyListedWhenTheTenantHasThatFeature() throws Exception {
        when(service.all(any())).thenReturn(List.of(ticket("CLOSED")));
        in(FULL, () -> {
            try {
                mvc().perform(get("/tickets").param("scope", "all").with(user("rosa"))).andExpect(status().isOk()).andExpect(jsonPath("$[0].state").value("CLOSED"));
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
            return null;
        });
    }
}
