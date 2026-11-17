package com.platform.security.jwt;

import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;

/**
 * The one place that wires platform authentication into a service's filter chain.
 * Services keep ownership of their own authorization rules:
 *
 * <pre>
 * http.authorizeHttpRequests(a -&gt; a.anyRequest().authenticated());
 * return PlatformSecurity.configure(http, registry).build();
 * </pre>
 */
public final class PlatformSecurity {

    private PlatformSecurity() {
    }

    public static HttpSecurity configure(HttpSecurity http, TrustedIssuerRegistry registry) throws Exception {
        return http
                .csrf(csrf -> csrf.disable()) // stateless bearer tokens, no cookies
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .oauth2ResourceServer(o -> o.authenticationManagerResolver(
                        new DynamicIssuerAuthenticationManagerResolver(registry)))
                .addFilterAfter(new TenantContextFilter(), BearerTokenAuthenticationFilter.class);
    }
}
