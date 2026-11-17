package com.platform.tenants;

import com.platform.tenants.config.PlatformProperties;
import com.platform.tenants.domain.Tenant;
import com.platform.tenants.domain.TenantRepository;
import com.platform.tenants.resolve.TenantResolver;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TenantResolverTest {

    private final TenantRepository repo = mock(TenantRepository.class);
    private final PlatformProperties props = new PlatformProperties("example.com", "https://auth.example.com",
            "http://keycloak:8180", "platform-automation", "x", List.of("platform-automation"), "platform-console", "platform-operator");
    private final TenantResolver resolver = new TenantResolver(repo, props);

    private static Tenant tenant(String name, String status) {
        return new Tenant(UUID.randomUUID(), name, name, null, null, "a@b.co", status, "ENTERPRISE", null, "NONE",
                name, 7, Instant.now());
    }

    @Test
    void firstLabelOfAHostUnderThePlatformDomainMatchesTheTenantName() {
        Tenant acme = tenant("acme", "active");
        when(repo.findByName("acme")).thenReturn(Optional.of(acme));

        assertThat(resolver.resolve("acme.example.com")).contains(acme);
        assertThat(resolver.resolve("ACME.Example.COM:443")).contains(acme);
    }

    @Test
    void codeBeatsDomainBeatsNameBeatsLabel() {
        Tenant byCode = tenant("by-code", "active");
        when(repo.findByCode("acme.example.com")).thenReturn(Optional.of(byCode));
        when(repo.findByDomain("acme.example.com")).thenReturn(Optional.of(tenant("by-domain", "active")));

        assertThat(resolver.resolve("acme.example.com")).contains(byCode);
        verify(repo, never()).findByDomain(anyString());
    }

    @Test
    void aLookalikeHostNeverMatchesAnotherTenant() {
        // The documented leak: "motivity.<domain>" must not match "reachmotivity.<domain>".
        when(repo.findByName("reachmotivity")).thenReturn(Optional.of(tenant("reachmotivity", "active")));
        when(repo.findByDomain("reachmotivity.example.com")).thenReturn(Optional.of(tenant("reachmotivity", "active")));

        assertThat(resolver.resolve("motivity.example.com")).isEmpty();
        verify(repo, never()).findByName("reachmotivity");
    }

    @Test
    void firstLabelRuleOnlyAppliesUnderThePlatformDomain() {
        when(repo.findByName("acme")).thenReturn(Optional.of(tenant("acme", "active")));

        assertThat(resolver.resolve("acme.evil.org")).isEmpty();
        assertThat(resolver.resolve("deep.acme.example.com")).isEmpty(); // more than one label before the domain
        assertThat(resolver.resolve("example.com")).isEmpty();
    }

    @Test
    void suspendedProvisioningAndOffboardedTenantsLookLikeUnknown() {
        for (String status : List.of("suspended", "provisioning", "offboarded")) {
            when(repo.findByName("acme")).thenReturn(Optional.of(tenant("acme", status)));
            assertThat(resolver.resolve("acme.example.com")).as(status).isEmpty();
        }
    }

    @Test
    void invalidHostsAreRejectedBeforeAnyLookup() {
        assertThat(resolver.resolve(null)).isEmpty();
        assertThat(resolver.resolve("")).isEmpty();
        assertThat(resolver.resolve("acme%.example.com")).isEmpty();
        assertThat(resolver.resolve("a_b.example.com")).isEmpty();
        assertThat(resolver.resolve("a b.example.com")).isEmpty();
        assertThat(resolver.resolve("x".repeat(300))).isEmpty();
        verify(repo, never()).findByCode(anyString());
    }

    @Test
    void normalizeStripsPortAndTrailingDot() {
        assertThat(TenantResolver.normalizeHost("Acme.Example.com.:8443")).contains("acme.example.com");
    }
}
