package com.platform.tenants;

import com.platform.tenants.config.SecurityConfig;

import com.platform.tenants.audit.Operator;
import com.platform.tenants.config.PlatformProperties;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Who may call the tenant management API: an automation client, or a person from the console who holds the role. */
class OperatorAccessTest {

    private static final PlatformProperties PROPS = new PlatformProperties("example.com", "https://auth.example.com",
            "http://keycloak:8180", "platform-automation", "x", List.of("platform-automation"), "platform-console", "platform-operator");

    private static Jwt token(Map<String, Object> claims) {
        Jwt.Builder b = Jwt.withTokenValue("t").header("alg", "none").subject(UUID.randomUUID().toString())
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60));
        claims.forEach(b::claim);
        return b.build();
    }

    private static Map<String, Object> roles(String... r) {
        return Map.of("roles", List.of(r));
    }

    @Test
    void anAllowListedAutomationClientIsEnough() {
        assertThat(SecurityConfig.mayManageTenants(token(Map.of("azp", "platform-automation")), PROPS)).isTrue();
    }

    @Test
    void aPersonNeedsBothTheConsoleClientAndTheRole() {
        assertThat(SecurityConfig.mayManageTenants(token(Map.of("azp", "platform-console", "realm_access", roles("platform-operator"))), PROPS)).isTrue();
        assertThat(SecurityConfig.mayManageTenants(token(Map.of("azp", "platform-console", "realm_access", roles("offline_access"))), PROPS)).isFalse();
        assertThat(SecurityConfig.mayManageTenants(token(Map.of("azp", "platform-console")), PROPS)).isFalse();
    }

    @Test
    void theRoleThroughSomeOtherClientIsNotEnoughAndNeitherIsAnyOtherClient() {
        assertThat(SecurityConfig.mayManageTenants(token(Map.of("azp", "account", "realm_access", roles("platform-operator"))), PROPS)).isFalse();
        assertThat(SecurityConfig.mayManageTenants(token(Map.of("azp", "acme")), PROPS)).isFalse();
        assertThat(SecurityConfig.mayManageTenants(token(Map.of("sub", "x")), PROPS)).isFalse();
    }

    @Test
    void aRoleClaimOfTheWrongShapeIsIgnored() {
        assertThat(SecurityConfig.mayManageTenants(token(Map.of("azp", "platform-console", "realm_access", "platform-operator")), PROPS)).isFalse();
        assertThat(SecurityConfig.mayManageTenants(token(Map.of("azp", "platform-console", "roles", List.of("platform-operator"))), PROPS)).isFalse();
    }

    @Test
    void theOperatorInAnAuditEventIsNamedByEmailThenUsername() {
        assertThat(Operator.of(token(Map.of("email", "ops@platform.test", "preferred_username", "ops"))).name()).isEqualTo("ops@platform.test");
        assertThat(Operator.of(token(Map.of("preferred_username", "service-account-platform-automation"))).name()).isEqualTo("service-account-platform-automation");
        assertThat(Operator.of(token(Map.of())).name()).isEqualTo("platform");
    }
}
