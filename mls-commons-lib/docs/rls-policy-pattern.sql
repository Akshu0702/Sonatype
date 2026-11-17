-- Row-level security pattern for every tenant-scoped table, in every service.
-- Copy into a Flyway migration and rename `example_widget`.
--
-- RlsConnectionProvider sets the session variable app.current_tenant when a
-- connection is acquired and clears it ('') before the connection returns to the
-- pool. NULLIF turns the cleared state into NULL, so the policies compare against
-- NULL, match nothing, and the connection sees ZERO rows. No tenant context means
-- no data, never "no filter".
--
-- (The earlier template cast current_setting() straight to uuid. After the value is
-- cleared that is the empty string, which raises "invalid input syntax for type
-- uuid" instead of denying cleanly. NULLIF fixes that.)

CREATE TABLE example_widget (
    id          UUID PRIMARY KEY DEFAULT uuidv7(),
    tenant_id   UUID NOT NULL,
    name        TEXT NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_example_widget_tenant ON example_widget (tenant_id);

-- FORCE: the table owner (often the role the service connects as) obeys the policies too.
ALTER TABLE example_widget ENABLE ROW LEVEL SECURITY;
ALTER TABLE example_widget FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON example_widget
    USING      (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid);

-- Cross-tenant access (platform-admin reporting, the tenant resolver) is a separate,
-- explicitly named role with BYPASSRLS, granted as its own reviewed change and
-- NEVER to the role a service connects as day to day.
