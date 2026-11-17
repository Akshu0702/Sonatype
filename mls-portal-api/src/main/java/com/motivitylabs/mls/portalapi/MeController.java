package com.motivitylabs.mls.portalapi;

import com.platform.security.features.TenantFeatures;
import com.platform.security.jwt.PlatformAuthentication;
import com.platform.security.jwt.PlatformPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/** Who is signed in and what their tenant may use. The console builds its menu from this. */
@RestController
public class MeController {

    public record Me(UUID tenantId, String subject, Set<String> products, Set<String> features) {
    }

    private final TenantFeatures features;

    public MeController(TenantFeatures features) {
        this.features = features;
    }

    @GetMapping("/me")
    public Me me(Authentication authentication) {
        PlatformPrincipal who = ((PlatformAuthentication) authentication).getPrincipal();
        return new Me(who.tenantId(), who.subject(), new TreeSet<>(features.products(who.tenantId())), new TreeSet<>(features.effective(who.tenantId())));
    }
}
