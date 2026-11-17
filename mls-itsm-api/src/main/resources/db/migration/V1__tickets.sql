-- ITSM Product: tickets for on-demand environments. Every table is tenant-scoped and protected by row-level security that is FORCED,
-- so even the table owner sees only the rows of the tenant named in the session variable app.current_tenant (set per transaction by
-- TenantDb from the verified token). With no tenant set, the database returns no rows at all.

CREATE TABLE ticket_counters (
    tenant_id   UUID PRIMARY KEY,
    last_number BIGINT NOT NULL
);

CREATE TABLE tickets (
    ticket_id       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL,
    ticket_number   BIGINT NOT NULL,
    title           TEXT NOT NULL,
    description     TEXT NOT NULL DEFAULT '',
    requester       TEXT NOT NULL,               -- the token's subject
    requester_name  TEXT,                        -- for display only
    state           TEXT NOT NULL,
    config          TEXT NOT NULL,               -- the environment configuration asked for
    ttl_hours       INTEGER NOT NULL,
    build_name      TEXT NOT NULL,               -- the uploaded file's name, for display; never used as a path
    build_sha256    TEXT NOT NULL,
    build_size      BIGINT NOT NULL,
    build_key       TEXT NOT NULL,               -- where the build store keeps it
    environment_ref TEXT,                        -- the provisioning request's reference, once there is one
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT tickets_number_unique UNIQUE (tenant_id, ticket_number),
    CONSTRAINT tickets_title_length  CHECK (length(title) BETWEEN 3 AND 200),
    CONSTRAINT tickets_description_length CHECK (length(description) <= 8000),
    CONSTRAINT tickets_state_known   CHECK (state IN ('RAISED', 'PENDING_APPROVAL', 'APPROVED', 'REJECTED', 'PROVISIONING', 'ACTIVE', 'TESTING', 'REPORTED', 'CLOSED')),
    CONSTRAINT tickets_ttl_range     CHECK (ttl_hours BETWEEN 1 AND 72),
    CONSTRAINT tickets_sha256_format CHECK (build_sha256 ~ '^[0-9a-f]{64}$'),
    CONSTRAINT tickets_build_size    CHECK (build_size > 0)
);
CREATE INDEX tickets_by_requester ON tickets (tenant_id, requester, created_at DESC);
CREATE INDEX tickets_by_state     ON tickets (tenant_id, state, created_at);

CREATE TABLE ticket_history (
    event_id   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id  UUID NOT NULL,
    ticket_id  UUID NOT NULL REFERENCES tickets (ticket_id),
    at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    actor      TEXT NOT NULL,                    -- who did it ('system' for provisioning)
    action     TEXT NOT NULL,                    -- raised, submitted, approved, rejected, transition, provisioning-requested ...
    from_state TEXT,
    to_state   TEXT,
    comment    TEXT
);
CREATE INDEX ticket_history_by_ticket ON ticket_history (ticket_id, event_id);

-- History is append-only: no update, no delete, for anyone.
CREATE FUNCTION ticket_history_append_only() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'ticket_history is append-only';
END
$$;
CREATE TRIGGER ticket_history_no_change BEFORE UPDATE OR DELETE ON ticket_history
    FOR EACH ROW EXECUTE FUNCTION ticket_history_append_only();

ALTER TABLE ticket_counters ENABLE ROW LEVEL SECURITY;
ALTER TABLE ticket_counters FORCE ROW LEVEL SECURITY;
ALTER TABLE tickets ENABLE ROW LEVEL SECURITY;
ALTER TABLE tickets FORCE ROW LEVEL SECURITY;
ALTER TABLE ticket_history ENABLE ROW LEVEL SECURITY;
ALTER TABLE ticket_history FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON ticket_counters
    USING (tenant_id = nullif(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = nullif(current_setting('app.current_tenant', true), '')::uuid);
CREATE POLICY tenant_isolation ON tickets
    USING (tenant_id = nullif(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = nullif(current_setting('app.current_tenant', true), '')::uuid);
CREATE POLICY tenant_isolation ON ticket_history
    USING (tenant_id = nullif(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = nullif(current_setting('app.current_tenant', true), '')::uuid);
