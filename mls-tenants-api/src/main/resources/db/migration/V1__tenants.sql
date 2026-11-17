-- uuidv7() is built into PostgreSQL 18. The database version for MLS is still open, so on 16 and 17 this adds an equivalent
-- (a time-ordered UUID: 48 bits of milliseconds, then random bits, with the version and variant set). When the database
-- already has uuidv7(), nothing is created and the built-in one is used.
DO $shim$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_proc p JOIN pg_namespace n ON n.oid = p.pronamespace WHERE p.proname = 'uuidv7' AND p.pronargs = 0) THEN
        CREATE FUNCTION public.uuidv7() RETURNS uuid LANGUAGE sql VOLATILE AS $fn$
            SELECT encode(
                set_bit(set_bit(
                    overlay(uuid_send(gen_random_uuid()) PLACING substring(int8send((extract(epoch FROM clock_timestamp()) * 1000)::bigint) FROM 3) FROM 1 FOR 6),
                    52, 1), 53, 1),
                'hex')::uuid
        $fn$;
    END IF;
END
$shim$;

-- Tenant registry. Deliberately NOT row-level-secured: it is the table that is read
-- BEFORE a tenant context exists (vanity-URL resolution, issuer lookup). Access is
-- controlled by the service's endpoints, and every lookup is an exact match.

CREATE TABLE tenants (
    tenant_id                   UUID PRIMARY KEY DEFAULT uuidv7(),
    tenant_name                 TEXT NOT NULL,          -- vanity label, lowercase; also the Keycloak realm name
    display_name                TEXT NOT NULL,
    tenant_code                 TEXT,                   -- explicit code for pre-registered clients
    domain                      TEXT,                   -- customer's own corporate domain
    admin_email                 TEXT NOT NULL,          -- first tenant administrator
    status                      TEXT NOT NULL DEFAULT 'provisioning',
    tenant_type                 TEXT NOT NULL,
    parent_tenant_id            UUID REFERENCES tenants (tenant_id),
    parent_access_level         TEXT NOT NULL DEFAULT 'NONE',
    realm_name                  TEXT NOT NULL,
    package_id                  UUID,
    provisioning_steps_completed INTEGER NOT NULL DEFAULT 0,
    provisioning_lease_until    TIMESTAMPTZ,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT tenants_name_format   CHECK (tenant_name ~ '^[a-z0-9][a-z0-9-]{1,28}[a-z0-9]$'),
    CONSTRAINT tenants_realm_is_name CHECK (realm_name = tenant_name),
    CONSTRAINT tenants_domain_lower  CHECK (domain IS NULL OR domain = lower(domain)),
    CONSTRAINT tenants_code_lower    CHECK (tenant_code IS NULL OR tenant_code = lower(tenant_code)),
    CONSTRAINT tenants_status        CHECK (status IN ('provisioning', 'active', 'suspended', 'offboarded')),
    CONSTRAINT tenants_type          CHECK (tenant_type IN ('MASTER_MSSP', 'MSSP', 'ENTERPRISE')),
    CONSTRAINT tenants_access_level  CHECK (parent_access_level IN ('NONE', 'READ_ONLY', 'IMPERSONATE')),
    CONSTRAINT tenants_no_self_parent CHECK (parent_tenant_id IS NULL OR parent_tenant_id <> tenant_id)
);

-- Exact-match lookups only; uniqueness is what makes "exact match" unambiguous.
CREATE UNIQUE INDEX uq_tenants_name   ON tenants (tenant_name);
CREATE UNIQUE INDEX uq_tenants_realm  ON tenants (realm_name);
CREATE UNIQUE INDEX uq_tenants_code   ON tenants (tenant_code) WHERE tenant_code IS NOT NULL;
CREATE UNIQUE INDEX uq_tenants_domain ON tenants (domain) WHERE domain IS NOT NULL;
CREATE INDEX idx_tenants_parent       ON tenants (parent_tenant_id);
