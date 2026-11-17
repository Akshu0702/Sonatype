package com.platform.security.autoconfigure;

import com.platform.security.jwt.RemoteTrustedIssuerRegistry;
import com.platform.security.jwt.TrustedIssuerRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * Services other than tenants-service get their issuer registry by setting
 * {@code platform.tenants.url}. tenants-service defines its own database-backed registry instead.
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "platform.tenants", name = "url")
public class RemoteIssuerRegistryAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(TrustedIssuerRegistry.class)
    TrustedIssuerRegistry remoteTrustedIssuerRegistry(
            @org.springframework.beans.factory.annotation.Value("${platform.tenants.url}") String url) {
        return new RemoteTrustedIssuerRegistry(url);
    }
}
