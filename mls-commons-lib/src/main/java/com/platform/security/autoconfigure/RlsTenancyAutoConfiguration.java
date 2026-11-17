package com.platform.security.autoconfigure;

import com.platform.security.tenancy.RlsConnectionProvider;
import com.platform.security.tenancy.TenantIdentifierResolver;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.engine.jdbc.connections.spi.MultiTenantConnectionProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;

import javax.sql.DataSource;

/**
 * Switches on tenant binding for every service that has a DataSource and Hibernate.
 * Disable with {@code platform.tenancy.rls.enabled=false} (only for a service that
 * has no tenant-scoped tables).
 */
@AutoConfiguration(after = DataSourceAutoConfiguration.class, before = HibernateJpaAutoConfiguration.class)
@ConditionalOnClass(MultiTenantConnectionProvider.class)
@ConditionalOnBean(DataSource.class)
@ConditionalOnProperty(prefix = "platform.tenancy.rls", name = "enabled", matchIfMissing = true)
public class RlsTenancyAutoConfiguration {

    @Bean
    HibernatePropertiesCustomizer platformRlsTenancyCustomizer(DataSource dataSource) {
        return properties -> {
            properties.put(AvailableSettings.MULTI_TENANT_CONNECTION_PROVIDER, new RlsConnectionProvider(dataSource));
            properties.put(AvailableSettings.MULTI_TENANT_IDENTIFIER_RESOLVER, new TenantIdentifierResolver());
        };
    }
}
