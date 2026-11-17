package com.platform.security.authz;

import com.platform.security.jwt.PlatformAuthentication;
import com.platform.security.jwt.PlatformPrincipal;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class RemotePermissionCheckerTest {

    private static final UUID TENANT = UUID.randomUUID();
    private HttpServer server;
    private String base;
    private final AtomicInteger calls = new AtomicInteger();
    private volatile int status = 200;
    private final java.util.List<String> queries = new java.util.concurrent.CopyOnWriteArrayList<>();

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/permissions", ex -> {
            calls.incrementAndGet();
            queries.add(ex.getRequestURI().getRawQuery());
            byte[] body = "[\"audit.read\",\"chat.use\"]".getBytes(StandardCharsets.UTF_8);
            if (status == 200) {
                ex.getResponseHeaders().add("Content-Type", "application/json");
                ex.sendResponseHeaders(200, body.length);
                ex.getResponseBody().write(body);
            } else {
                ex.sendResponseHeaders(status, -1);
            }
            ex.close();
        });
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort();
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "none").subject("anita")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
        SecurityContextHolder.getContext().setAuthentication(
                new PlatformAuthentication(new PlatformPrincipal(TENANT, "anita", "https://a/realms/x"), jwt, List.of()));
    }

    @AfterEach
    void stop() {
        server.stop(0);
        SecurityContextHolder.clearContext();
    }

    @Test
    void grantsOnlyWhatIamReportsAndCachesTheAnswer() {
        RemotePermissionChecker checker = new RemotePermissionChecker(base, Duration.ofSeconds(30));

        assertThat(checker.has("audit.read")).isTrue();
        assertThat(checker.has("users.write")).isFalse();
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void askedAsAPersonaIamIsToldWhichOneAndEachPersonaIsCachedApart() {
        RemotePermissionChecker checker = new RemotePermissionChecker(base, Duration.ofSeconds(30));

        checker.permissions(TENANT, "anita", "FDE");
        checker.permissions(TENANT, "anita", "APPROVER");
        checker.permissions(TENANT, "anita", "FDE");
        checker.permissions(TENANT, "anita");

        assertThat(calls.get()).isEqualTo(3);
        assertThat(queries.get(0)).contains("persona=FDE");
        assertThat(queries.get(1)).contains("persona=APPROVER");
        assertThat(queries.get(2)).endsWith("persona=");
    }

    @Test
    void failsClosedAndDoesNotCacheTheFailure() {
        RemotePermissionChecker checker = new RemotePermissionChecker(base, Duration.ofSeconds(30));
        status = 500;
        assertThat(checker.has("audit.read")).isFalse();

        status = 200;
        assertThat(checker.has("audit.read")).isTrue();
    }

    @Test
    void unreachableIamDenies() {
        assertThat(new RemotePermissionChecker("http://127.0.0.1:1", Duration.ofSeconds(30)).has("audit.read")).isFalse();
    }

    @Test
    void anonymousCallerIsDenied() {
        SecurityContextHolder.clearContext();
        assertThat(new RemotePermissionChecker(base, Duration.ofSeconds(30)).has("audit.read")).isFalse();
        assertThat(calls.get()).isZero();
    }

    @Test
    void permissionsAreKeyedPerTenantAndSubject() {
        RemotePermissionChecker checker = new RemotePermissionChecker(base, Duration.ofSeconds(30));
        checker.permissions(TENANT, "anita");
        checker.permissions(UUID.randomUUID(), "anita");
        checker.permissions(TENANT, "bob");

        assertThat(calls.get()).isEqualTo(3);
    }
}
