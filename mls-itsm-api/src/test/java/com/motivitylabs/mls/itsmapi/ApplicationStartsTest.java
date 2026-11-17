package com.motivitylabs.mls.itsmapi;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The whole service starts with its real beans against a real PostgreSQL (migrations run, tenant binding in place), and the startup check
 * (mode "fail", as in any real environment) accepts the feature keys the code uses against a catalogue that has them. A misspelt key in the
 * code makes this test fail. Only PostgreSQL and tenants-service are needed: no IAM, no portal API.
 */
@EnabledIfEnvironmentVariable(named = "TEST_PG_URL", matches = ".+")
@SpringBootTest(properties = {"platform.features.check=fail", "itsm.approvers=rosa"})
class ApplicationStartsTest {

    private static HttpServer tenants;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry r) throws Exception {
        TestDb.create("itsm_app_test");
        tenants = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        tenants.createContext("/internal/catalogue", ex -> {
            byte[] b = ("{\"products\":[\"platform\",\"itsm\"],\"features\":[\"itsm.tickets.tickets_view_own\",\"itsm.tickets.tickets_view_all\","
                    + "\"itsm.tickets.tickets_create_provisioning\",\"itsm.tickets.tickets_attach\",\"itsm.tickets.tickets_transition\","
                    + "\"itsm.approvals.approvals_queue\",\"itsm.approvals.approvals_decide\"]}").getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(200, b.length);
            ex.getResponseBody().write(b);
            ex.close();
        });
        tenants.start();
        r.add("platform.tenants.url", () -> "http://127.0.0.1:" + tenants.getAddress().getPort());
        r.add("spring.datasource.url", () -> TestDb.url("itsm_app_test"));
        r.add("spring.datasource.username", () -> TestDb.OWNER);
        r.add("spring.datasource.password", () -> TestDb.PASSWORD);
        r.add("itsm.storage-dir", () -> System.getProperty("java.io.tmpdir") + "/itsm-app-test");
    }

    @AfterAll
    static void stop() {
        tenants.stop(0);
    }

    @Autowired
    TicketsController controller;

    @Autowired
    ProvisioningClient provisioning;

    @Autowired
    BuildStore store;

    @Autowired
    ItsmProperties properties;

    @Test
    void settingsAreReadFromTheEnvironmentAndTheDefaultsApply() {
        assertThat(properties.approvers()).containsExactly("rosa");
        assertThat(properties.configurations()).isNotEmpty();
        assertThat(properties.uploadMaxBytes()).isEqualTo(100L * 1024 * 1024);
        assertThat(properties.corsOrigins()).isEmpty();
    }

    @Test
    void theServiceStartsWithOnlyPostgresAndTenantsAndItsFeatureKeysAreInTheCatalogue() {
        assertThat(controller).isNotNull();
    }

    @Test
    void untilTheRealProvisioningApiExistsTheFakeAnswers() {
        assertThat(provisioning).isInstanceOf(FakeProvisioningClient.class);
        assertThat(store).isInstanceOf(LocalDiskBuildStore.class);
    }
}
