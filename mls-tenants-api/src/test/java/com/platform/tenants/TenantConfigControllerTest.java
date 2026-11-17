package com.platform.tenants;

import com.platform.tenants.api.TenantConfigController;
import com.platform.tenants.config.PlatformProperties;
import com.platform.tenants.domain.Tenant;
import com.platform.tenants.domain.TenantRepository;
import com.platform.tenants.resolve.DbTrustedIssuerRegistry;
import com.platform.tenants.resolve.TenantResolver;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TenantConfigControllerTest {

    private static final UUID ID = UUID.randomUUID();
    private final TenantRepository repo = mock(TenantRepository.class);
    private final PlatformProperties props = new PlatformProperties("example.com", "https://auth.example.com",
            "http://keycloak:8080", "platform-automation", "x", List.of("platform-automation"), "platform-console", "platform-operator");
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(
            new TenantConfigController(new TenantResolver(repo, props), new DbTrustedIssuerRegistry(repo, props), repo)).build();

    private void acmeIsActive() {
        when(repo.findByName("acme")).thenReturn(Optional.of(new Tenant(ID, "acme", "Acme", null, null, "a@b.co",
                "active", "ENTERPRISE", null, "NONE", "acme", 7, Instant.now())));
    }

    @Test
    void resolveReturnsTenantIdRealmAndIssuer() throws Exception {
        acmeIsActive();
        mvc.perform(get("/internal/resolve").param("host", "acme.example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value(ID.toString()))
                .andExpect(jsonPath("$.issuer").value("https://auth.example.com/realms/acme"));
    }

    @Test
    void resolveOfAnUnknownHostIs404() throws Exception {
        mvc.perform(get("/internal/resolve").param("host", "nobody.example.com")).andExpect(status().isNotFound());
    }

    @Test
    void adminLookupWorksOnlyForActiveTenants() throws Exception {
        when(repo.findById(ID)).thenReturn(Optional.of(new Tenant(ID, "acme", "Acme", null, null, "boss@acme.test",
                "active", "ENTERPRISE", null, "NONE", "acme", 7, Instant.now())));
        mvc.perform(get("/internal/tenants/" + ID + "/admin")).andExpect(status().isOk())
                .andExpect(jsonPath("$.adminEmail").value("boss@acme.test"));
        when(repo.findById(ID)).thenReturn(Optional.of(new Tenant(ID, "acme", "Acme", null, null, "boss@acme.test",
                "provisioning", "ENTERPRISE", null, "NONE", "acme", 1, Instant.now())));
        mvc.perform(get("/internal/tenants/" + ID + "/admin")).andExpect(status().isNotFound());
    }

    @Test
    void publicConfigDoesNotExposeTheTenantId() throws Exception {
        acmeIsActive();
        mvc.perform(get("/tenant-config").param("host", "acme.example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").doesNotExist())
                .andExpect(jsonPath("$.realmName").value("acme"));
    }

    @Test
    void browserVariantRequiresRefererToMatchHost() throws Exception {
        acmeIsActive();
        mvc.perform(get("/tenant-config/v1").param("host", "acme.example.com")).andExpect(status().isNotFound());
        mvc.perform(get("/tenant-config/v1").param("host", "acme.example.com")
                .header("Referer", "https://other.example.com/login")).andExpect(status().isNotFound());
        mvc.perform(get("/tenant-config/v1").param("host", "acme.example.com")
                .header("Referer", "https://acme.example.com/login")).andExpect(status().isOk());
    }
}
