package com.platform.security.jwt;

import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Optional;

/**
 * Asks tenants-service which tenant owns an issuer: {@code GET {baseUrl}/internal/trusted-issuer?issuer=}.
 * 404 means "not trusted". Any other failure throws, so authentication fails closed while
 * tenants-service is unreachable (the caller never caches an outage).
 */
public class RemoteTrustedIssuerRegistry implements TrustedIssuerRegistry {

    private final RestClient http;

    public RemoteTrustedIssuerRegistry(String baseUrl) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(2));
        factory.setReadTimeout(Duration.ofSeconds(3));
        this.http = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }

    @Override
    public Optional<TrustedIssuer> find(String issuer) {
        try {
            return Optional.ofNullable(http.get()
                    .uri("/internal/trusted-issuer?issuer={issuer}", issuer)
                    .retrieve().body(TrustedIssuer.class));
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        }
    }
}
