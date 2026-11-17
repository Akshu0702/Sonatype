package com.platform.tenants;

import com.platform.tenants.config.PlatformProperties;
import com.platform.tenants.keycloak.RestKeycloakAdmin;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/** The Keycloak admin client against a stand-in Keycloak. */
class RestKeycloakAdminTest {

    @Test
    void aNewRealmGetsAFreshTokenBecauseAnOldOneDoesNotReachIt() throws Exception {
        AtomicInteger tokens = new AtomicInteger();
        HttpServer kc = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        kc.createContext("/", ex -> {
            String path = ex.getRequestURI().getPath();
            byte[] body;
            int status = 200;
            if (path.equals("/realms/master/protocol/openid-connect/token")) {
                body = ("{\"access_token\":\"t" + tokens.incrementAndGet() + "\",\"expires_in\":300}").getBytes(StandardCharsets.UTF_8);
            } else if (ex.getRequestMethod().equals("POST") && path.equals("/admin/realms")) {
                body = new byte[0];
                status = 201;
            } else {
                body = "[]".getBytes(StandardCharsets.UTF_8);
            }
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(status, body.length == 0 ? -1 : body.length);
            if (body.length > 0) {
                ex.getResponseBody().write(body);
            }
            ex.close();
        });
        kc.start();
        try {
            String url = "http://127.0.0.1:" + kc.getAddress().getPort();
            RestKeycloakAdmin admin = new RestKeycloakAdmin(new PlatformProperties("localhost", url, url, "platform-automation", "s", List.of("platform-automation"), "platform-console", "platform-operator"), "mls");
            admin.realmExists("master");
            assertThat(tokens).hasValue(1);
            admin.createRealm("acme", "Acme");
            admin.clientExists("acme", "acme");
            assertThat(tokens).as("a token is requested again once the realm exists").hasValue(2);
        } finally {
            kc.stop(0);
        }
    }
}
