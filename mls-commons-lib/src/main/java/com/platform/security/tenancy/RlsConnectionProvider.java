package com.platform.security.tenancy;

import org.hibernate.engine.jdbc.connections.spi.MultiTenantConnectionProvider;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;

/**
 * Binds every connection Hibernate hands out to a tenant by setting the Postgres
 * session variable {@code app.current_tenant}, which the row-level security
 * policies compare against {@code tenant_id}. This is the runtime half of
 * PLATFORM-SPEC §15.2 #13; the policies in the migrations are the other half.
 *
 * <p>The variable is set when a connection is acquired and cleared before it goes
 * back to the pool, so a pooled connection can never carry one tenant's identity
 * into another tenant's request. If clearing fails the connection is physically
 * aborted instead of being returned: a connection of unknown state never re-enters
 * the pool.
 *
 * <p>(The previous template did this in a servlet filter with {@code SET LOCAL}.
 * That does nothing: the filter was not inside the transaction that runs the
 * queries, so the setting expired before the first one.)
 */
public class RlsConnectionProvider implements MultiTenantConnectionProvider<String> {

    private static final String SET_TENANT = "SELECT set_config('app.current_tenant', ?, false)";
    private static final String UNSET = "";

    private final DataSource dataSource;

    public RlsConnectionProvider(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Connection getAnyConnection() throws SQLException {
        return dataSource.getConnection();
    }

    @Override
    public void releaseAnyConnection(Connection connection) throws SQLException {
        connection.close();
    }

    @Override
    public Connection getConnection(String tenantIdentifier) throws SQLException {
        Connection connection = dataSource.getConnection();
        try {
            bind(connection, tenantValue(tenantIdentifier));
            return connection;
        } catch (SQLException | RuntimeException e) {
            abortQuietly(connection);
            throw e;
        }
    }

    @Override
    public void releaseConnection(String tenantIdentifier, Connection connection) throws SQLException {
        try {
            bind(connection, UNSET);
        } catch (SQLException | RuntimeException e) {
            abortQuietly(connection);
            throw e;
        }
        connection.close();
    }

    @Override
    public boolean supportsAggressiveRelease() {
        return false;
    }

    @Override
    public boolean isUnwrappableAs(Class<?> unwrapType) {
        return false;
    }

    @Override
    public <U> U unwrap(Class<U> unwrapType) {
        throw new UnsupportedOperationException("Cannot unwrap to " + unwrapType);
    }

    private static String tenantValue(String tenantIdentifier) {
        if (tenantIdentifier == null || TenantIdentifierResolver.NO_TENANT.equals(tenantIdentifier)) {
            return UNSET;
        }
        // Only a well-formed UUID may reach the session variable.
        return UUID.fromString(tenantIdentifier).toString();
    }

    private static void bind(Connection connection, String value) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(SET_TENANT)) {
            statement.setString(1, value);
            statement.execute();
        }
    }

    private static void abortQuietly(Connection connection) {
        try {
            connection.abort(Runnable::run);
        } catch (SQLException | RuntimeException ignored) {
            // Nothing more can be done; the pool will evict it on its own validation.
        }
    }
}
