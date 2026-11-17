package com.motivitylabs.mls.portalapi;

import com.platform.security.jwt.PlatformSecurity;
import com.platform.security.jwt.TrustedIssuerRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/** Every API call needs a token from a tenant's own realm; the shared library works out which tenant it is. */
@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain api(HttpSecurity http, TrustedIssuerRegistry registry) throws Exception {
        http.authorizeHttpRequests(a -> a
                .requestMatchers("/actuator/health/**").permitAll()
                .requestMatchers("/error").permitAll()
                .anyRequest().authenticated());
        return PlatformSecurity.configure(http, registry).build();
    }
}
