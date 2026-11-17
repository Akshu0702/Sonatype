package com.motivitylabs.mls.itsmapi;

import org.flywaydb.core.Flyway;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;

/**
 * A fresh database owned by an ordinary (not super) user, migrated with the real migrations. Row-level security does not apply to a superuser,
 * so the tests must connect as someone who is not one; the policies are FORCED, so the owner is held to them too.
 */
final class TestDb {

    static final String OWNER = "itsm_owner";
    static final String PASSWORD = "itsm-test";

    private TestDb() {
    }

    static boolean available() {
        return System.getenv("TEST_PG_URL") != null && !System.getenv("TEST_PG_URL").isBlank();
    }

    static String url(String database) {
        String admin = System.getenv("TEST_PG_URL");
        return admin.substring(0, admin.lastIndexOf('/') + 1) + database;
    }

    static DataSource create(String database) throws Exception {
        DriverManagerDataSource admin = new DriverManagerDataSource(System.getenv("TEST_PG_URL"), System.getenv("TEST_PG_ADMIN_USER"), System.getenv("TEST_PG_ADMIN_PASSWORD"));
        try (Connection c = admin.getConnection(); Statement s = c.createStatement()) {
            boolean role = s.executeQuery("SELECT 1 FROM pg_roles WHERE rolname = '" + OWNER + "'").next();
            if (!role) {
                s.execute("CREATE ROLE " + OWNER + " LOGIN PASSWORD '" + PASSWORD + "' NOSUPERUSER NOBYPASSRLS");
            }
            s.execute("DROP DATABASE IF EXISTS " + database + " WITH (FORCE)");
            s.execute("CREATE DATABASE " + database + " OWNER " + OWNER);
        }
        DriverManagerDataSource owner = new DriverManagerDataSource(url(database), OWNER, PASSWORD);
        Flyway.configure().dataSource(owner).load().migrate();
        return owner;
    }
}
