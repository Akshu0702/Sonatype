package com.motivitylabs.mls.portalapi;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The whole application starts with its real beans, and the startup check (mode "fail", as in any real environment) accepts the feature
 * keys the code uses against a catalogue that has them. A key that is misspelt in the code makes this test fail.
 */
@SpringBootTest(properties = "platform.features.check=fail")
class ApplicationStartsTest {

    private static final HttpServer TENANTS = start();

    private static HttpServer start() {
        try {
            HttpServer s = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            s.createContext("/internal/catalogue", ex -> {
                byte[] b = ("{\"products\":[\"platform\",\"automation\"],\"features\":["
                        + "\"automation.deployments_and_environments.environments_view\"]}").getBytes(StandardCharsets.UTF_8);
                ex.getResponseHeaders().add("Content-Type", "application/json");
                ex.sendResponseHeaders(200, b.length);
                ex.getResponseBody().write(b);
                ex.close();
            });
            s.start();
            return s;
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void tenants(DynamicPropertyRegistry r) {
        r.add("platform.tenants.url", () -> "http://127.0.0.1:" + TENANTS.getAddress().getPort());
    }

    @AfterAll
    static void stop() {
        TENANTS.stop(0);
    }

    @Autowired
    EnvironmentsController environments;

    @Test
    void theApplicationStartsAndItsFeatureKeysAreInTheCatalogue() {
        assertThat(environments).isNotNull();
    }
}
