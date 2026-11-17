package com.motivitylabs.mls.itsmapi;

import com.platform.security.tenancy.TenantContext;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.util.UUID;
import java.util.function.Function;

/**
 * Runs database work for the tenant of the current request. The tenant comes from {@link TenantContext}, which the shared library fills in
 * from the VERIFIED token, never from request input. It is written into the transaction ({@code set_config(..., true)}), so a pooled
 * connection can never carry one tenant's identity into the next request, and the row-level-security policies see only that tenant's rows.
 */
@Component
public class TenantDb {

    private final JdbcClient jdbc;
    private final TransactionTemplate tx;

    public TenantDb(DataSource dataSource, PlatformTransactionManager transactions) {
        this.jdbc = JdbcClient.create(dataSource);
        this.tx = new TransactionTemplate(transactions);
    }

    public <T> T inTenant(Function<JdbcClient, T> work) {
        UUID tenant = TenantContext.require();
        return tx.execute(status -> {
            jdbc.sql("SELECT set_config('app.current_tenant', :tenant, true)").param("tenant", tenant.toString()).query().singleRow();
            return work.apply(jdbc);
        });
    }
}
