package com.platform.tenants.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    /**
     * Tenant management: only tokens issued by the MASTER realm to an allow-listed automation client.
     * (Human platform operators arrive with the IAM service.)
     */
    @Bean
    @Order(1)
    SecurityFilterChain managementChain(HttpSecurity http, PlatformProperties props) throws Exception {
        return http
                .securityMatcher("/api/**")
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a.anyRequest().authenticated())
                .oauth2ResourceServer(o -> o.jwt(j -> j.decoder(masterDecoder(props))))
                .build();
    }

    /**
     * Resolution and issuer-lookup endpoints carry no user identity. They are reachable only on the
     * container network: the edge and the gateway never route /internal/** or /tenant-config to the public.
     */
    @Bean
    @Order(2)
    SecurityFilterChain openChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/tenant-config", "/tenant-config/v1",
                                "/internal/trusted-issuer", "/internal/resolve").permitAll()
                        .requestMatchers("/internal/tenants/*/admin").permitAll()
                        .requestMatchers("/internal/tenants/*/features", "/internal/tenants/*/features/*/enabled", "/internal/catalogue").permitAll()
                        .requestMatchers("/actuator/health/**").permitAll()
                        // When the API rejects a request, Spring forwards to /error to write the answer. Denying that page
                        // turned every 400/404/409 into an empty 403. It carries only the status and our own short message.
                        .requestMatchers("/error").permitAll()
                        .anyRequest().denyAll())
                .build();
    }

    /**
     * Two kinds of caller: an allow-listed automation client (a service account), or a person who came through the
     * operator console's client AND holds the operator realm role. Holding the role through some other client is not enough.
     */
    public static boolean mayManageTenants(Jwt jwt, PlatformProperties props) {
        String client = jwt.getClaimAsString("azp");
        if (client == null) {
            return false;
        }
        if (props.adminClients().contains(client)) {
            return true;
        }
        if (!client.equals(props.operatorClientId())) {
            return false;
        }
        Object realmAccess = jwt.getClaim("realm_access");
        if (realmAccess instanceof java.util.Map<?, ?> map && map.get("roles") instanceof java.util.Collection<?> roles) {
            return roles.contains(props.operatorRole());
        }
        return false;
    }

    private JwtDecoder masterDecoder(PlatformProperties props) {
        String issuer = props.authServerUrl() + "/realms/master";
        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withJwkSetUri(props.keycloakInternalUrl() + "/realms/master/protocol/openid-connect/certs")
                .build();
        OAuth2TokenValidator<Jwt> allowedClient = jwt -> mayManageTenants(jwt, props)
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "client or role not allowed", null));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(issuer), allowedClient));
        return decoder;
    }
}
