package com.platform.security.features;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/** Against a stand-in for tenants-service: Products, the cache, and what happens when it cannot be reached. */
class TenantFeaturesTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final String BODY = """
            [{"key":"platform.administration.users_manage","product":"platform","entitled":true,"enabled":true,"effective":true},
             {"key":"itsm.tickets.tickets_create","product":"itsm","entitled":true,"enabled":true,"effective":true},
             {"key":"itsm.reports.reports_view","product":"itsm","entitled":true,"enabled":true,"effective":false},
             {"key":"automation.status.environments_view","product":"automation","entitled":false,"enabled":true,"effective":false}]""";

    private HttpServer server;
    private final AtomicInteger calls = new AtomicInteger();
    private volatile int status = 200;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/tenants/" + TENANT + "/features", ex -> {
            calls.incrementAndGet();
            byte[] body = BODY.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(status, status == 200 ? body.length : -1);
            if (status == 200) {
                ex.getResponseBody().write(body);
            }
            ex.close();
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private TenantFeatures features(Duration ttl) {
        return new TenantFeatures("http://127.0.0.1:" + server.getAddress().getPort(), ttl);
    }

    @Test
    void aFeatureIsOnOnlyWhenItIsLiveAndAProductIsOnWhenOneOfItsFeaturesIs() {
        TenantFeatures f = features(Duration.ofSeconds(30));
        assertThat(f.enabled(TENANT, "itsm.tickets.tickets_create")).isTrue();
        assertThat(f.enabled(TENANT, "itsm.reports.reports_view")).isFalse();                // entitled but not live
        assertThat(f.enabled(TENANT, "automation.status.environments_view")).isFalse();      // not entitled
        assertThat(f.enabled(TENANT, "itsm.tickets.never_heard_of")).isFalse();
        assertThat(f.products(TENANT)).containsExactlyInAnyOrder("platform", "itsm");
        assertThat(f.productOn(TENANT, "itsm")).isTrue();
        assertThat(f.productOn(TENANT, "automation")).isFalse();
    }

    @Test
    void theAnswerIsKeptForTheCacheTimeAndFetchedAgainAfterIt() throws InterruptedException {
        TenantFeatures f = features(Duration.ofMillis(200));
        f.enabled(TENANT, "itsm.tickets.tickets_create");
        f.productOn(TENANT, "itsm");
        f.effective(TENANT);
        assertThat(calls.get()).isEqualTo(1);
        Thread.sleep(300);
        f.enabled(TENANT, "itsm.tickets.tickets_create");
        assertThat(calls.get()).isEqualTo(2);
        f.forget(TENANT);
        f.enabled(TENANT, "itsm.tickets.tickets_create");
        assertThat(calls.get()).isEqualTo(3);
    }

    @Test
    void anOutageKeepsTheLastAnswer() throws InterruptedException {
        TenantFeatures f = features(Duration.ofMillis(100));
        assertThat(f.enabled(TENANT, "itsm.tickets.tickets_create")).isTrue();
        status = 500;
        Thread.sleep(200);
        assertThat(f.enabled(TENANT, "itsm.tickets.tickets_create")).isTrue();               // stale, but still the last known answer
        assertThat(f.enabled(TENANT, "automation.status.environments_view")).isFalse();
        assertThat(f.productOn(TENANT, "itsm")).isTrue();
    }

    @Test
    void withNoEarlierAnswerAnOutageTurnsOffEverythingOptionalButNotThePlatform() {
        status = 500;
        TenantFeatures f = features(Duration.ofSeconds(30));
        assertThat(f.enabled(TENANT, "itsm.tickets.tickets_create")).isFalse();
        assertThat(f.productOn(TENANT, "itsm")).isFalse();
        assertThat(f.productOn(TENANT, "automation")).isFalse();
        assertThat(f.enabled(TENANT, "platform.administration.users_manage")).isTrue();     // sign-in, administration and audit keep working
        assertThat(f.productOn(TENANT, "platform")).isTrue();
        assertThat(f.effective(TENANT)).isEmpty();
    }

    @Test
    void aKnownAnswerIsNeverWidenedToThePlatformFeaturesItDoesNotList() {
        // a tenant that tenants-service has answered for, with no platform feature live (for example a suspended tenant), gets none
        status = 200;
        TenantFeatures f = features(Duration.ofSeconds(30));
        assertThat(f.enabled(TENANT, "platform.administration.nothing_here")).isFalse();
    }

    @Test
    void aRefusalForTheTenantIsAnAnswerNotAnOutageSoNothingIsOnNotEvenThePlatform() {
        status = 404;
        TenantFeatures f = features(Duration.ofSeconds(30));
        assertThat(f.enabled(TENANT, "platform.administration.users_manage")).isFalse();
        assertThat(f.productOn(TENANT, "platform")).isFalse();
        assertThat(f.enabled(TENANT, "itsm.tickets.tickets_create")).isFalse();
        f.enabled(TENANT, "itsm.tickets.tickets_create");
        assertThat(calls.get()).isEqualTo(1);                                               // remembered, not asked again at once
    }

    @Test
    void anUnreachableServerIsHandledAsAnOutage() {
        server.stop(0);
        TenantFeatures f = new TenantFeatures("http://127.0.0.1:1", Duration.ofSeconds(30));
        assertThat(f.enabled(TENANT, "itsm.tickets.tickets_create")).isFalse();
        assertThat(f.enabled(TENANT, "platform.administration.users_manage")).isTrue();
    }
}
