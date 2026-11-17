package com.platform.security.autoconfigure;

import com.platform.security.authz.RemotePermissionChecker;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

import java.time.Duration;

/** Registers the {@code perms} bean when {@code platform.iam.url} is set (every service except IAM itself). */
@AutoConfiguration
@ConditionalOnProperty(prefix = "platform.iam", name = "url")
public class RemotePermissionAutoConfiguration {

    @Bean(name = "perms")
    RemotePermissionChecker remotePermissionChecker(@Value("${platform.iam.url}") String url,
                                                    @Value("${platform.iam.cache-seconds:15}") long seconds) {
        return new RemotePermissionChecker(url, Duration.ofSeconds(seconds));
    }
}
