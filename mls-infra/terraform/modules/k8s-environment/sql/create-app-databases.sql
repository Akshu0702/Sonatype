-- Creates the application databases and users of one environment (UAT or Prod) on its RDS instance.
-- Run ONCE, by an administrator, from inside the VPC (see ../iam-by-hand.md, "Create the database users"), as the master user.
-- Every application user is a plain login: NOT a superuser, NOT able to bypass row-level security, NOT able to create roles or databases.
-- The passwords are not in this file. Set each one from the secret of the same name (psql variables, below); never type one into a ticket.
--
--   psql "host=<address> dbname=postgres user=dbadmin sslmode=require" \
--        -v tenants_password="$(aws secretsmanager get-secret-value --secret-id mls/<env>/tenants/db-password --query SecretString --output text)" \
--        -v itsm_password="..." -v harbor_password="..." -f create-app-databases.sql

\set ON_ERROR_STOP on

SELECT format('CREATE ROLE %I LOGIN NOSUPERUSER NOBYPASSRLS NOCREATEDB NOCREATEROLE NOINHERIT PASSWORD %L', r, p)
FROM (VALUES ('tenants', :'tenants_password'), ('itsm', :'itsm_password'), ('harbor', :'harbor_password')) AS v(r, p)
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = v.r)
\gexec

-- The master user must be a member of a role to create a database owned by it.
GRANT tenants, itsm, harbor TO CURRENT_USER;

SELECT format('CREATE DATABASE %I OWNER %I', d, o)
FROM (VALUES ('tenants', 'tenants'), ('itsm', 'itsm'), ('registry', 'harbor')) AS v(d, o)
WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = v.d)
\gexec

-- Nobody else may connect to an application database, and nothing is left in the public schema for everyone.
REVOKE ALL ON DATABASE tenants FROM PUBLIC;
REVOKE ALL ON DATABASE itsm FROM PUBLIC;
REVOKE ALL ON DATABASE registry FROM PUBLIC;

REVOKE tenants, itsm, harbor FROM CURRENT_USER;

-- Check: all three users, none a superuser, none able to bypass row-level security.
SELECT rolname, rolsuper, rolbypassrls, rolcreatedb, rolcreaterole
FROM pg_roles WHERE rolname IN ('tenants', 'itsm', 'harbor') ORDER BY rolname;
