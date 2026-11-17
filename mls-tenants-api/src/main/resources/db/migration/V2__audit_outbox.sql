-- Platform operations on a tenant (create, suspend, reset the admin password ...) are recorded in the tenant's own
-- audit trail through this outbox, which a relay publishes to Kafka. Not tenant-scoped, like the registry itself.
CREATE TABLE audit_outbox (
    id         BIGSERIAL PRIMARY KEY,
    event_id   UUID NOT NULL UNIQUE,
    tenant_id  UUID NOT NULL,
    payload    JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    sent_at    TIMESTAMPTZ
);
CREATE INDEX idx_audit_outbox_pending ON audit_outbox (id) WHERE sent_at IS NULL;
CREATE INDEX idx_audit_outbox_sent ON audit_outbox (sent_at) WHERE sent_at IS NOT NULL;
