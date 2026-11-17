-- The tenant's own switch. What a tenant may use comes from its package and overrides (V4); the tenant's administrator may then turn a
-- feature the platform enforces off for their own organisation (enabled, on unless they say otherwise). A feature is live only when it is
-- entitled, switched on here and the features it needs are live too. A tenant with no row for a feature has not switched it off.
-- Like the tenant registry this is read by other services before any tenant context exists, so it is reached by exact tenant id through the service.
CREATE TABLE tenant_features (
    tenant_id  UUID NOT NULL REFERENCES tenants (tenant_id) ON DELETE CASCADE,
    feature    TEXT NOT NULL,
    enabled    BOOLEAN NOT NULL DEFAULT true,
    updated_by TEXT,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (tenant_id, feature),
    CONSTRAINT feature_key CHECK (feature ~ '^[a-z][a-z0-9]*(\.[a-z][a-z0-9_]*)+$')
);
