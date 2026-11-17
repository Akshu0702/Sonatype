package com.platform.security.jwt;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthenticationToken;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Real signatures, a real JWKS endpoint, no mocks of the verification path. */
class DynamicIssuerAuthenticationManagerResolverTest {

    private static final UUID TENANT_ACME = UUID.fromString("00000000-0000-0000-0000-00000000acce");

    private HttpServer jwksServer;
    private RSAKey acmeKey;
    private String acmeIssuer;

    private final Map<String, TrustedIssuer> trusted = new ConcurrentHashMap<>();
    private final AtomicInteger lookups = new AtomicInteger();
    private DynamicIssuerAuthenticationManagerResolver resolver;

    @BeforeEach
    void setUp() throws Exception {
        acmeKey = new RSAKeyGenerator(2048).keyID("acme-1").generate();
        String jwks = new JWKSet(acmeKey.toPublicJWK()).toString();

        jwksServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        jwksServer.createContext("/certs", exchange -> {
            byte[] body = jwks.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        jwksServer.start();

        String base = "http://127.0.0.1:" + jwksServer.getAddress().getPort();
        acmeIssuer = base + "/realms/acme";
        trusted.put(acmeIssuer, new TrustedIssuer(acmeIssuer, base + "/certs", TENANT_ACME));

        resolver = new DynamicIssuerAuthenticationManagerResolver(issuer -> {
            lookups.incrementAndGet();
            return Optional.ofNullable(trusted.get(issuer));
        });
    }

    @AfterEach
    void tearDown() {
        jwksServer.stop(0);
    }

    @Test
    void validTokenAuthenticatesAsTheTenantOfItsIssuer() throws Exception {
        Authentication result = authenticate(token(acmeKey, acmeIssuer, 300));

        assertThat(result).isInstanceOf(PlatformAuthentication.class);
        PlatformPrincipal principal = ((PlatformAuthentication) result).getPrincipal();
        assertThat(principal.tenantId()).isEqualTo(TENANT_ACME);
        assertThat(principal.subject()).isEqualTo("anita");
        assertThat(result.getAuthorities()).isEmpty(); // permissions come from our RBAC, not the token
    }

    @Test
    void tenantComesFromTheIssuerRegistrationNotFromAClaimInTheToken() throws Exception {
        String token = sign(acmeKey, new JWTClaimsSet.Builder()
                .issuer(acmeIssuer).subject("anita")
                .claim("tenant_id", "11111111-1111-1111-1111-111111111111")
                .expirationTime(new Date(System.currentTimeMillis() + 300_000)).build());

        PlatformAuthentication result = (PlatformAuthentication) authenticate(token);

        assertThat(result.getPrincipal().tenantId()).isEqualTo(TENANT_ACME);
    }

    @Test
    void unknownIssuerIsRejected() throws Exception {
        String token = token(acmeKey, "https://evil.example.org/realms/acme", 300);

        assertThatThrownBy(() -> resolver.resolve(request(token))).isInstanceOf(InvalidBearerTokenException.class);
    }

    @Test
    void issuerMustMatchExactlyNotByPrefixOrSubstring() throws Exception {
        // The registered issuer is ".../realms/acme"; a longer lookalike must not match it.
        String lookalike = acmeIssuer + "-corp";

        assertThatThrownBy(() -> resolver.resolve(request(token(acmeKey, lookalike, 300))))
                .isInstanceOf(InvalidBearerTokenException.class);
    }

    @Test
    void tokenSignedWithAnotherKeyIsRejected() throws Exception {
        RSAKey attacker = new RSAKeyGenerator(2048).keyID("acme-1").generate(); // same kid, different key

        assertThatThrownBy(() -> authenticate(token(attacker, acmeIssuer, 300)))
                .isInstanceOf(AuthenticationException.class);
    }

    @Test
    void expiredTokenIsRejected() throws Exception {
        assertThatThrownBy(() -> authenticate(token(acmeKey, acmeIssuer, -3600)))
                .isInstanceOf(AuthenticationException.class);
    }

    @Test
    void oversizedTokenIsRejectedBeforeAnyLookup() {
        String huge = "a".repeat(DynamicIssuerAuthenticationManagerResolver.MAX_TOKEN_CHARS + 1);

        assertThatThrownBy(() -> resolver.resolve(request(huge))).isInstanceOf(InvalidBearerTokenException.class);
        assertThat(lookups.get()).isZero();
    }

    @Test
    void malformedAndUnsignedTokensAreRejected() {
        assertThatThrownBy(() -> resolver.resolve(request("not-a-jwt"))).isInstanceOf(InvalidBearerTokenException.class);
        assertThatThrownBy(() -> resolver.resolve(request("eyJhbGciOiJub25lIn0.e30."))).isInstanceOf(InvalidBearerTokenException.class);
        assertThat(lookups.get()).isZero();
    }

    @Test
    void knownIssuerIsLookedUpOnceThenServedFromCache() throws Exception {
        authenticate(token(acmeKey, acmeIssuer, 300));
        authenticate(token(acmeKey, acmeIssuer, 300));
        authenticate(token(acmeKey, acmeIssuer, 300));

        assertThat(lookups.get()).isEqualTo(1);
    }

    @Test
    void unknownIssuerIsNotLookedUpAgainWithinTheNegativeCacheWindow() throws Exception {
        String token = token(acmeKey, "https://evil.example.org/realms/x", 300);

        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> resolver.resolve(request(token))).isInstanceOf(InvalidBearerTokenException.class);
        }
        assertThat(lookups.get()).isEqualTo(1);
    }

    @Test
    void registryOutageFailsClosedAndIsNotCached() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        DynamicIssuerAuthenticationManagerResolver flaky = new DynamicIssuerAuthenticationManagerResolver(issuer -> {
            if (calls.incrementAndGet() == 1) {
                throw new IllegalStateException("tenants service down");
            }
            return Optional.ofNullable(trusted.get(issuer));
        });
        String token = token(acmeKey, acmeIssuer, 300);

        assertThatThrownBy(() -> flaky.resolve(request(token))).isInstanceOf(AuthenticationServiceException.class);
        // recovers on the next request instead of remembering the outage
        AuthenticationManager manager = flaky.resolve(request(token));
        assertThat(manager.authenticate(new BearerTokenAuthenticationToken(token)).isAuthenticated()).isTrue();
    }

    // ---- helpers ----

    private Authentication authenticate(String token) {
        return resolver.resolve(request(token)).authenticate(new BearerTokenAuthenticationToken(token));
    }

    private static MockHttpServletRequest request(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        return request;
    }

    private static String token(RSAKey key, String issuer, long validForSeconds) throws Exception {
        return sign(key, new JWTClaimsSet.Builder()
                .issuer(issuer).subject("anita")
                .issueTime(new Date(System.currentTimeMillis() - 7_200_000))
                .expirationTime(new Date(System.currentTimeMillis() + validForSeconds * 1000))
                .build());
    }

    private static String sign(RSAKey key, JWTClaimsSet claims) throws Exception {
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(), claims);
        jwt.sign(new RSASSASigner(key));
        return jwt.serialize();
    }
}
