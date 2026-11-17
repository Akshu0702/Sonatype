package com.platform.tenants;

import com.platform.tenants.keycloak.KeycloakAdmin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/** The whole application starts with its real beans (the unit tests use a fake Keycloak client, so they cannot catch a bean that cannot be built). */
@EnabledIfEnvironmentVariable(named = "TEST_PG_URL", matches = ".+")
@SpringBootTest(properties = {"platform.audit.relay-enabled=false", "spring.kafka.bootstrap-servers=localhost:1"})
class ApplicationStartsTest {

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", () -> System.getenv("TEST_PG_URL"));
        r.add("spring.datasource.username", () -> System.getenv("TEST_PG_ADMIN_USER"));
        r.add("spring.datasource.password", () -> System.getenv("TEST_PG_ADMIN_PASSWORD"));
    }

    @Autowired
    KeycloakAdmin keycloak;

    @Test
    void theRealKeycloakClientIsWiredIn() {
        assertThat(keycloak).isInstanceOf(com.platform.tenants.keycloak.RestKeycloakAdmin.class);
    }
}
