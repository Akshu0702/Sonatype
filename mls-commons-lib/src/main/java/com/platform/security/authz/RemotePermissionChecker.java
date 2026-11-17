package com.platform.security.authz;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.platform.security.jwt.PlatformAuthentication;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.client.RestClient;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;

/**
 * Lets any service ask IAM whether the caller holds a permission, for use as
 * {@code @PreAuthorize("@perms.has('audit.read')")}. IAM stays the only owner of RBAC data.
 *
 * Answers are cached briefly (a revoked permission stops working within {@code ttl}). It fails CLOSED:
 * if IAM cannot be reached the answer is "no", and a failure is never cached.
 */
public class RemotePermissionChecker {

    public static final String PERSONA_HEADER = "X-Persona";
    private static final Logger log = LoggerFactory.getLogger(RemotePermissionChecker.class);

    private final RestClient http;
    private final Cache<String, Set<String>> cache;

    public RemotePermissionChecker(String iamBaseUrl, Duration ttl) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(2));
        factory.setReadTimeout(Duration.ofSeconds(3));
        this.http = RestClient.builder().baseUrl(iamBaseUrl).requestFactory(factory).build();
        this.cache = Caffeine.newBuilder().maximumSize(50_000).expireAfterWrite(ttl).build();
    }

    public boolean has(String permission) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof PlatformAuthentication platform)) {
            return false;
        }
        return permissions(platform.getPrincipal().tenantId(), platform.getPrincipal().subject(), requestedPersona()).contains(permission);
    }

    Set<String> permissions(UUID tenantId, String subject) {
        return permissions(tenantId, subject, null);
    }

    /**
     * The role the person says they are working as (the {@code X-Persona} header). IAM only ever narrows to a role they really hold;
     * anything else is ignored there, so the header cannot widen access.
     */
    private static String requestedPersona() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes servlet) {
            String header = servlet.getRequest().getHeader(PERSONA_HEADER);
            return header == null || header.isBlank() || header.length() > 80 ? null : header.trim();
        }
        return null;
    }

    Set<String> permissions(UUID tenantId, String subject, String persona) {
        String key = tenantId + "|" + subject + "|" + (persona == null ? "" : persona);
        Set<String> cached = cache.getIfPresent(key);
        if (cached != null) {
            return cached;
        }
        try {
            Set<String> fetched = http.get()
                    .uri("/internal/permissions?tenantId={t}&sub={s}&persona={p}", tenantId, subject, persona == null ? "" : persona)
                    .retrieve().body(new ParameterizedTypeReference<Set<String>>() { });
            Set<String> result = fetched == null ? Set.of() : Set.copyOf(fetched);
            cache.put(key, result);
            return result;
        } catch (RuntimeException e) {
            log.warn("Permission lookup failed; denying", e);
            return Set.of();
        }
    }
}
