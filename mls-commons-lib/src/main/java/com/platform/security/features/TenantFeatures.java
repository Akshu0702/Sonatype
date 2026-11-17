package com.platform.security.features;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.platform.security.tenancy.TenantContext;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Which features, and so which Products, a tenant has. tenants-service holds the switches and answers with every feature of the tenant: the
 * Product it belongs to, whether the tenant may have it (entitled), whether the tenant's administrator has left it on (enabled), and whether it is
 * live (effective: both hold and the feature it needs is live too). This asks for the answer and keeps it for a few seconds, so a change takes
 * effect within that time everywhere. Registered as the {@code features} bean, so a rule can say {@code @features.on('itsm.tickets.tickets_view_own')}.
 *
 * <p>A feature key is {@code <product>.<group>.<name>}. A Product is on for a tenant when at least one of its features is live; a Product
 * that is off answers 404 for every API in it, so a service can guard a whole Product with {@link #productOn(String)}.
 *
 * <p>If tenants-service cannot be reached (or fails with a server error) the last answer for the tenant is used. With no earlier answer, everything optional is off, but the
 * {@value #PLATFORM} Product stays on: it is mandatory and in no package, so an outage of tenants-service must not stop sign-in, administration or audit.
 * A refusal (4xx, for example an unknown tenant) is not an outage: nothing is on for that tenant, platform included.
 */
public class TenantFeatures {

    /** The mandatory Product: always on, so it is also the answer when nothing is known. */
    public static final String PLATFORM = "platform";

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record State(String key, String product, boolean entitled, boolean enabled, boolean effective) {
    }

    private record Entry(Instant expires, Set<String> effective, Set<String> products) {
    }

    /** Nothing is known about the tenant: tenants-service has not answered yet. */
    private static final Entry UNKNOWN = new Entry(Instant.MIN, Set.of(), Set.of());

    private final RestClient http;
    private final Duration ttl;
    private final Map<UUID, Entry> cache = new ConcurrentHashMap<>();

    public TenantFeatures(String tenantsUrl, Duration ttl) {
        this(client(tenantsUrl), ttl);
    }

    TenantFeatures(RestClient http, Duration ttl) {
        this.http = http;
        this.ttl = ttl;
    }

    private static RestClient client(String tenantsUrl) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(2));
        factory.setReadTimeout(Duration.ofSeconds(3));
        return RestClient.builder().baseUrl(tenantsUrl).requestFactory(factory).build();
    }

    /** For rules: is this feature on for the tenant of the current request? */
    public boolean on(String feature) {
        return enabled(TenantContext.require(), feature);
    }

    /** For rules and guards: does the tenant of the current request have this Product? */
    public boolean productOn(String product) {
        return productOn(TenantContext.require(), product);
    }

    public boolean enabled(UUID tenantId, String feature) {
        Entry e = entry(tenantId);
        return e.effective().contains(feature) || (e == UNKNOWN && feature.startsWith(PLATFORM + "."));
    }

    public boolean productOn(UUID tenantId, String product) {
        Entry e = entry(tenantId);
        return e.products().contains(product) || (e == UNKNOWN && PLATFORM.equals(product));
    }

    public Set<String> effective(UUID tenantId) {
        return entry(tenantId).effective();
    }

    /** The Products with at least one live feature. */
    public Set<String> products(UUID tenantId) {
        return entry(tenantId).products();
    }

    private Entry entry(UUID tenantId) {
        Entry e = cache.get(tenantId);
        if (e != null && e.expires().isAfter(Instant.now())) {
            return e;
        }
        try {
            List<State> states = http.get().uri("/internal/tenants/{id}/features", tenantId).retrieve().body(new ParameterizedTypeReference<>() { });
            List<State> live = (states == null ? List.<State>of() : states).stream().filter(State::effective).toList();
            Entry fresh = new Entry(Instant.now().plus(ttl), live.stream().map(State::key).collect(Collectors.toUnmodifiableSet()),
                    live.stream().map(State::product).filter(p -> p != null).collect(Collectors.toUnmodifiableSet()));
            cache.put(tenantId, fresh);
            return fresh;
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().is4xxClientError()) {
                // an unknown or refused tenant is an answer, not an outage: nothing is on, and that is remembered for a moment
                Entry none = new Entry(Instant.now().plus(ttl), Set.of(), Set.of());
                cache.put(tenantId, none);
                return none;
            }
            return e != null ? e : UNKNOWN;
        } catch (RestClientException ex) {
            return e != null ? e : UNKNOWN;   // an outage keeps the last answer; without one, only the platform Product runs
        }
    }

    /** The Products and features that exist, for the startup check of the keys code uses. Throws when tenants-service cannot be reached. */
    public FeatureCodeCheck.Catalogue catalogue() {
        Catalogue c = http.get().uri("/internal/catalogue").retrieve().body(Catalogue.class);
        if (c == null) {
            throw new IllegalStateException("tenants-service sent an empty catalogue");
        }
        return new FeatureCodeCheck.Catalogue(Set.copyOf(c.products()), Set.copyOf(c.features()));
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Catalogue(List<String> products, List<String> features) {
    }

    /** Forget a tenant's answer, for a change this process has just made itself. */
    public void forget(UUID tenantId) {
        cache.remove(tenantId);
    }
}
