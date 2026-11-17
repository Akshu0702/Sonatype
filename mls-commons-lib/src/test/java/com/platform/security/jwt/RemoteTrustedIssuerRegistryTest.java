package com.platform.security.jwt;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RemoteTrustedIssuerRegistryTest {

    private static final UUID TENANT = UUID.randomUUID();
    private HttpServer server;
    private String base;
    private volatile int mode = 200;

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/trusted-issuer", ex -> {
            byte[] body = ("{\"issuer\":\"https://a/realms/x\",\"jwkSetUri\":\"http://k/certs\",\"tenantId\":\"" + TENANT + "\"}")
                    .getBytes(StandardCharsets.UTF_8);
            if (mode == 200) {
                ex.getResponseHeaders().add("Content-Type", "application/json");
                ex.sendResponseHeaders(200, body.length);
                ex.getResponseBody().write(body);
            } else {
                ex.sendResponseHeaders(mode, -1);
            }
            ex.close();
        });
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void knownIssuerIsReturned() {
        assertThat(new RemoteTrustedIssuerRegistry(base).find("https://a/realms/x"))
                .hasValueSatisfying(i -> assertThat(i.tenantId()).isEqualTo(TENANT));
    }

    @Test
    void notFoundMeansNotTrusted() {
        mode = 404;
        assertThat(new RemoteTrustedIssuerRegistry(base).find("https://a/realms/x")).isEmpty();
    }

    @Test
    void serverErrorFailsClosedInsteadOfLookingLikeNotTrusted() {
        mode = 500;
        assertThatThrownBy(() -> new RemoteTrustedIssuerRegistry(base).find("https://a/realms/x")).isInstanceOf(RuntimeException.class);
    }

    @Test
    void unreachableRegistryFailsClosed() {
        assertThatThrownBy(() -> new RemoteTrustedIssuerRegistry("http://127.0.0.1:1").find("x")).isInstanceOf(RuntimeException.class);
    }
}
