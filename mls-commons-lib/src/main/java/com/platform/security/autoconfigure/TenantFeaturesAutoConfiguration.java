package com.platform.security.autoconfigure;

import com.platform.security.features.FeatureCodeStartupCheck;
import com.platform.security.features.FeatureGuardInterceptor;
import com.platform.security.features.TenantFeatures;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.Duration;

/**
 * Registers the {@code features} bean when {@code platform.tenants.url} is set (every service except tenants-service itself), the 404 guard for
 * {@code @RequiresFeature}, and the startup check of the feature keys the code uses.
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "platform.tenants", name = "url")
public class TenantFeaturesAutoConfiguration {

    @Bean(name = "features")
    TenantFeatures tenantFeatures(@Value("${platform.tenants.url}") String url, @Value("${platform.features.cache-seconds:10}") long seconds) {
        return new TenantFeatures(url, Duration.ofSeconds(seconds));
    }

    @Bean
    WebMvcConfigurer featureGuard(TenantFeatures features) {
        return new WebMvcConfigurer() {
            @Override
            public void addInterceptors(InterceptorRegistry registry) {
                registry.addInterceptor(new FeatureGuardInterceptor(features));
            }
        };
    }

    @Bean
    FeatureCodeStartupCheck featureCodeStartupCheck(ApplicationContext context, Environment env, TenantFeatures features,
                                                           @Value("${platform.features.check-attempts:3}") int attempts, @Value("${platform.features.check-retry-ms:2000}") long retryMs) {
        return new FeatureCodeStartupCheck(context, features::catalogue, FeatureCodeStartupCheck.Mode.resolve(env), attempts, retryMs);
    }
}
