package com.platform.security.jwt;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.nimbusds.jwt.JWTParser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationManagerResolver;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationProvider;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;

import java.text.ParseException;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Validates tokens from any of thousands of per-tenant issuers (PLATFORM-SPEC §3.1).
 *
 * <p>The {@code iss} claim is read BEFORE the signature is verified, and is used for
 * exactly one thing: choosing which issuer's keys to verify against. The real
 * verification (signature, expiry, issuer equality) is done by the standard decoder
 * built for that issuer. The peek is bounded: oversized or malformed tokens are
 * rejected before any parsing or lookup work.
 *
 * <p>Every rejection looks identical to the caller; a token cannot be used to learn
 * which issuers exist.
 */
public final class DynamicIssuerAuthenticationManagerResolver implements AuthenticationManagerResolver<HttpServletRequest> {

    static final int MAX_TOKEN_CHARS = 8192;

    private final TrustedIssuerRegistry registry;
    private final BearerTokenResolver tokenResolver = new DefaultBearerTokenResolver();

    // Decoders hold a JWKS cache of their own, so they are built once per issuer, not per request.
    private final Cache<String, AuthenticationManager> managers = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(Duration.ofMinutes(5))
            .build();

    // Unknown issuers are remembered briefly so a flood of bogus tokens cannot become a lookup flood.
    private final Cache<String, Boolean> unknownIssuers = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(Duration.ofSeconds(30))
            .build();

    public DynamicIssuerAuthenticationManagerResolver(TrustedIssuerRegistry registry) {
        this.registry = registry;
    }

    @Override
    public AuthenticationManager resolve(HttpServletRequest request) {
        String token = tokenResolver.resolve(request);
        if (token == null || token.length() > MAX_TOKEN_CHARS) {
            throw invalid();
        }
        String issuer = peekIssuer(token);

        if (unknownIssuers.getIfPresent(issuer) != null) {
            throw invalid();
        }
        AuthenticationManager cached = managers.getIfPresent(issuer);
        if (cached != null) {
            return cached;
        }

        Optional<TrustedIssuer> trusted;
        try {
            trusted = registry.find(issuer);
        } catch (RuntimeException e) {
            // Registry unavailable: fail closed, and do not cache the failure.
            throw new AuthenticationServiceException("Unable to verify token", e);
        }
        if (trusted.isEmpty()) {
            unknownIssuers.put(issuer, Boolean.TRUE);
            throw invalid();
        }
        AuthenticationManager manager = buildManager(trusted.get());
        managers.put(issuer, manager);
        return manager;
    }

    private static String peekIssuer(String token) {
        try {
            String issuer = JWTParser.parse(token).getJWTClaimsSet().getIssuer();
            if (issuer == null || issuer.isBlank()) {
                throw invalid();
            }
            return issuer;
        } catch (ParseException e) {
            throw invalid();
        }
    }

    private static AuthenticationManager buildManager(TrustedIssuer trusted) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(trusted.jwkSetUri()).build();
        OAuth2TokenValidator<Jwt> validator = JwtValidators.createDefaultWithIssuer(trusted.issuer());
        decoder.setJwtValidator(validator);

        JwtAuthenticationProvider provider = new JwtAuthenticationProvider(decoder);
        // The tenant comes from the issuer's registration, not from the token.
        provider.setJwtAuthenticationConverter(jwt -> new PlatformAuthentication(
                new PlatformPrincipal(trusted.tenantId(), jwt.getSubject(), trusted.issuer()), jwt, List.of()));
        return new ProviderManager(provider);
    }

    private static InvalidBearerTokenException invalid() {
        return new InvalidBearerTokenException("Invalid bearer token");
    }
}
