-- Products, packages and entitlements.
--
-- A tenant is the wrapper; what it can use is a set of PRODUCTS (the platform itself, ITSM, AWS Automation), each with its own feature groups and features.
-- What a tenant may use is resolved in layers, the later winning:
--   1. the Product: on when the tenant's package includes it, or a tenant override of the Product says so   (package_products, tenant_overrides kind 'product')
--   2. the feature's own default                                                                            (features.default_on)
--   3. the package: the groups it includes and the features it switches                                     (package_groups, package_features)
--   4. a tenant override of a whole group                                                                  (tenant_overrides kind 'group')
--   5. a tenant override of one feature                                                                    (tenant_overrides kind 'feature')
--   6. the tenant administrator's own switch, for the features the platform enforces                      (tenant_features)
-- A Product that is off is off for every feature in it, whatever else says. The platform Product is mandatory: always on, in no package, not overridable.
-- A feature is usable only when it is on, the feature it needs is usable, and the tenant is active.
-- Overrides carry a reason and may expire; they never change the package. A published package version is read-only: a change is a new version.
-- Like the tenant registry, these tables are read before any tenant context exists, so they are reached by exact key through the service.

CREATE TABLE products (
    key       TEXT PRIMARY KEY,
    name      TEXT NOT NULL,
    mandatory BOOLEAN NOT NULL DEFAULT false,
    sort      INTEGER NOT NULL,
    CONSTRAINT product_key CHECK (key ~ '^[a-z][a-z0-9]*$')
);

CREATE TABLE feature_groups (
    key         TEXT PRIMARY KEY,
    product_key TEXT NOT NULL REFERENCES products (key),
    name        TEXT NOT NULL,
    mandatory   BOOLEAN NOT NULL DEFAULT false,
    sort        INTEGER NOT NULL
);

CREATE TABLE features (
    key         TEXT PRIMARY KEY,
    group_key   TEXT NOT NULL REFERENCES feature_groups (key),
    title       TEXT NOT NULL,
    description TEXT NOT NULL DEFAULT '',
    needs       TEXT REFERENCES features (key),
    -- a new feature is off until a package switches it on
    default_on  BOOLEAN NOT NULL DEFAULT false,
    -- true once the platform actually switches the feature on and off; the rest are listed so packages can be planned around them
    enforced    BOOLEAN NOT NULL DEFAULT false,
    sort        INTEGER NOT NULL,
    -- <product>.<group>.<name>
    CONSTRAINT feature_key CHECK (key ~ '^[a-z][a-z0-9]*(\.[a-z][a-z0-9_]*){2,}$')
);

CREATE TABLE packages (
    key         TEXT NOT NULL,
    version     INTEGER NOT NULL,
    name        TEXT NOT NULL,
    type        TEXT NOT NULL,
    status      TEXT NOT NULL DEFAULT 'draft',
    is_public   BOOLEAN NOT NULL DEFAULT true,
    price       TEXT NOT NULL DEFAULT '',
    description TEXT NOT NULL DEFAULT '',
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (key, version),
    CONSTRAINT package_status CHECK (status IN ('draft', 'active', 'retired')),
    CONSTRAINT package_key CHECK (key ~ '^[a-z][a-z0-9-]{1,30}$')
);
-- at most one live version per package
CREATE UNIQUE INDEX uq_one_active_package ON packages (key) WHERE status = 'active';

CREATE TABLE package_products (
    pkg_key     TEXT NOT NULL,
    pkg_version INTEGER NOT NULL,
    product_key TEXT NOT NULL REFERENCES products (key),
    PRIMARY KEY (pkg_key, pkg_version, product_key),
    FOREIGN KEY (pkg_key, pkg_version) REFERENCES packages (key, version) ON DELETE CASCADE
);

-- Which groups of an included Product the package has. (A Product a tenant gets only by override brings all its groups.)
CREATE TABLE package_groups (
    pkg_key     TEXT NOT NULL,
    pkg_version INTEGER NOT NULL,
    group_key   TEXT NOT NULL REFERENCES feature_groups (key),
    PRIMARY KEY (pkg_key, pkg_version, group_key),
    FOREIGN KEY (pkg_key, pkg_version) REFERENCES packages (key, version) ON DELETE CASCADE
);

-- Only where a package differs from a feature's default.
CREATE TABLE package_features (
    pkg_key     TEXT NOT NULL,
    pkg_version INTEGER NOT NULL,
    feature     TEXT NOT NULL REFERENCES features (key),
    on_         BOOLEAN NOT NULL,
    PRIMARY KEY (pkg_key, pkg_version, feature),
    FOREIGN KEY (pkg_key, pkg_version) REFERENCES packages (key, version) ON DELETE CASCADE
);

CREATE TABLE package_limits (
    pkg_key     TEXT NOT NULL,
    pkg_version INTEGER NOT NULL,
    limit_key   TEXT NOT NULL,
    value       BIGINT,                      -- null means unlimited
    PRIMARY KEY (pkg_key, pkg_version, limit_key),
    FOREIGN KEY (pkg_key, pkg_version) REFERENCES packages (key, version) ON DELETE CASCADE,
    CONSTRAINT limit_value CHECK (value IS NULL OR value >= 0)
);

ALTER TABLE tenants ADD COLUMN package_key TEXT NOT NULL DEFAULT 'lab-full';
ALTER TABLE tenants ADD COLUMN package_version INTEGER NOT NULL DEFAULT 1;

CREATE TABLE tenant_overrides (
    tenant_id  UUID NOT NULL REFERENCES tenants (tenant_id) ON DELETE CASCADE,
    kind       TEXT NOT NULL,
    key        TEXT NOT NULL,
    on_        BOOLEAN NOT NULL,
    reason     TEXT NOT NULL,
    until      DATE,
    updated_by TEXT,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (tenant_id, kind, key),
    CONSTRAINT override_kind CHECK (kind IN ('product', 'group', 'feature')),
    CONSTRAINT override_reason CHECK (length(btrim(reason)) > 0)
);

CREATE TABLE tenant_limit_overrides (
    tenant_id  UUID NOT NULL REFERENCES tenants (tenant_id) ON DELETE CASCADE,
    limit_key  TEXT NOT NULL,
    value      BIGINT,
    reason     TEXT NOT NULL,
    updated_by TEXT,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (tenant_id, limit_key),
    CONSTRAINT limit_override_value CHECK (value IS NULL OR value >= 0),
    CONSTRAINT limit_override_reason CHECK (length(btrim(reason)) > 0)
);

-- A feature's key starts with its group's key (which starts with its Product's), so the key alone says where it belongs.
CREATE FUNCTION feature_key_in_group() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF left(NEW.key, length(NEW.group_key) + 1) <> NEW.group_key || '.' THEN
        RAISE EXCEPTION 'feature key % must start with its group key %.', NEW.key, NEW.group_key USING ERRCODE = 'check_violation';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER features_key_in_group BEFORE INSERT OR UPDATE OF key, group_key ON features FOR EACH ROW EXECUTE FUNCTION feature_key_in_group();

-- ---------------------------------------------------------------- the catalogue: 65 features in 10 groups under 3 Products
-- The 65 portal permissions designed earlier, grouped by Product. Each feature's key is <product>.<group>.<permission with underscores>.
-- None is enforced yet: the platform does not switch any of them until its service is built. No feature declares a dependency yet.
INSERT INTO products (key, name, mandatory, sort) VALUES
    ('platform', 'Platform', true, 1),
    ('itsm', 'ITSM', false, 2),
    ('automation', 'AWS Automation', false, 3);

INSERT INTO feature_groups (key, product_key, name, sort) VALUES
    ('platform.administration', 'platform', 'Administration', 1),
    ('platform.audit_and_access_review', 'platform', 'Audit and access review', 2),
    ('itsm.tickets', 'itsm', 'Tickets', 1),
    ('itsm.approvals', 'itsm', 'Approvals', 2),
    ('itsm.service_management', 'itsm', 'Service management', 3),
    ('itsm.reports', 'itsm', 'Reports', 4),
    ('itsm.notifications', 'itsm', 'Notifications', 5),
    ('automation.deployments_and_environments', 'automation', 'Deployments and environments', 1),
    ('automation.testing_and_evidence', 'automation', 'Testing and evidence', 2),
    ('automation.status', 'automation', 'Status', 3);

INSERT INTO features (key, group_key, title, description, default_on, enforced, sort) VALUES
    ('platform.administration.users_read', 'platform.administration', 'View users', 'See people, their roles and groups', true, false, 1),
    ('platform.administration.users_write', 'platform.administration', 'Invite and disable users', 'Invite a person, enable or disable them', true, false, 2),
    ('platform.administration.groups_read', 'platform.administration', 'View groups', 'See groups, members and roles', true, false, 3),
    ('platform.administration.groups_write', 'platform.administration', 'Manage groups', 'Create and delete groups, set members, and give a group reach to environments', true, false, 4),
    ('platform.administration.roles_read', 'platform.administration', 'View roles and scopes', 'See roles and the scope catalogue', true, false, 5),
    ('platform.administration.roles_write', 'platform.administration', 'Manage roles and their scopes', 'Create custom roles, decide which scopes each role holds, set a person''s and a group''s roles', true, false, 6),
    ('platform.administration.sso_manage', 'platform.administration', 'Manage single sign-on', 'Connect and change the identity-provider federation', true, false, 7),
    ('platform.administration.flags_manage', 'platform.administration', 'Turn features on and off', 'Switch a scope''s flag', true, false, 8),
    ('platform.administration.settings_manage', 'platform.administration', 'Manage portal settings', 'Portal-wide settings', true, false, 9),
    ('platform.administration.sla_policies_manage', 'platform.administration', 'Manage SLA policies', 'Clocks and targets', true, false, 10),
    ('platform.administration.notifications_templates', 'platform.administration', 'Manage notification templates', 'Wording of messages', true, false, 11),
    ('platform.audit_and_access_review.audit_view', 'platform.audit_and_access_review', 'Search the audit log', 'Search audit events', true, false, 1),
    ('platform.audit_and_access_review.audit_verify', 'platform.audit_and_access_review', 'Verify the audit chain', 'Check that the audit record is intact', true, false, 2),
    ('platform.audit_and_access_review.access_recertify', 'platform.audit_and_access_review', 'Recertify access', 'Quarterly review of roles and accounts', true, false, 3),
    ('itsm.tickets.tickets_create_support', 'itsm.tickets', 'Create a support ticket', 'Ask a question or report a problem with the lab', true, false, 1),
    ('itsm.tickets.tickets_create_provisioning', 'itsm.tickets', 'Create a provisioning ticket', 'Ask for a test environment', true, false, 2),
    ('itsm.tickets.tickets_create_change', 'itsm.tickets', 'Create a plugin or environment change ticket', 'Ask for a plugin deployment, upgrade or environment change, with the build file', true, false, 3),
    ('itsm.tickets.tickets_create_incident', 'itsm.tickets', 'Create an incident ticket', 'Report something that is broken', true, false, 4),
    ('itsm.tickets.tickets_create_problem', 'itsm.tickets', 'Create a problem ticket', 'Record the underlying cause of repeated incidents', true, false, 5),
    ('itsm.tickets.tickets_create_access', 'itsm.tickets', 'Create an access ticket', 'Ask to join, change or leave (joiner, mover, leaver)', true, false, 6),
    ('itsm.tickets.tickets_view_own', 'itsm.tickets', 'View own tickets', 'See tickets you raised or follow', true, false, 7),
    ('itsm.tickets.tickets_view_all', 'itsm.tickets', 'View all tickets', 'See every ticket in your organisation scope', true, false, 8),
    ('itsm.tickets.tickets_update', 'itsm.tickets', 'Edit a ticket', 'Change description and fields while the ticket is open', true, false, 9),
    ('itsm.tickets.tickets_comment', 'itsm.tickets', 'Comment on a ticket', 'Add information or answer a question', true, false, 10),
    ('itsm.tickets.tickets_attach', 'itsm.tickets', 'Attach files', 'Upload a build ZIP or supporting file', true, false, 11),
    ('itsm.tickets.tickets_watch', 'itsm.tickets', 'Follow a ticket', 'Get notified of every change', true, false, 12),
    ('itsm.tickets.tickets_chase', 'itsm.tickets', 'Ask for an update', 'Send a reminder to the assignee without opening a new ticket', true, false, 13),
    ('itsm.tickets.tickets_escalate', 'itsm.tickets', 'Escalate a ticket', 'Raise it along the escalation path when the SLA is at risk', true, false, 14),
    ('itsm.tickets.tickets_cancel', 'itsm.tickets', 'Cancel a ticket', 'Withdraw your own ticket before work starts', true, false, 15),
    ('itsm.tickets.tickets_reopen', 'itsm.tickets', 'Reopen a ticket', 'Reopen a closed ticket within the allowed window', true, false, 16),
    ('itsm.tickets.tickets_assign', 'itsm.tickets', 'Assign a ticket', 'Assign or reassign to a person', true, false, 17),
    ('itsm.tickets.tickets_transition', 'itsm.tickets', 'Move a ticket between states', 'Manual transitions that are not automatic', true, false, 18),
    ('itsm.approvals.approvals_queue', 'itsm.approvals', 'See the approval queue', 'Tickets waiting for a decision', true, false, 1),
    ('itsm.approvals.approvals_decide', 'itsm.approvals', 'Approve or reject', 'Decide a deploy or restart, with a reason', true, false, 2),
    ('itsm.approvals.approvals_history', 'itsm.approvals', 'See approval history', 'Who decided what and when, on tickets you can see', true, false, 3),
    ('itsm.service_management.changes_manage', 'itsm.service_management', 'Manage change records', 'Risk, rollback plan and window', true, false, 1),
    ('itsm.service_management.incidents_view', 'itsm.service_management', 'View incidents', 'Open and recent incidents', true, false, 2),
    ('itsm.service_management.incidents_manage', 'itsm.service_management', 'Manage incidents', 'Triage, update and resolve', true, false, 3),
    ('itsm.service_management.problems_manage', 'itsm.service_management', 'Manage problems', 'Root cause and linked incidents', true, false, 4),
    ('itsm.service_management.sla_view', 'itsm.service_management', 'View SLA clocks', 'Time left and breaches on tickets you can see', true, false, 5),
    ('itsm.service_management.kpi_view', 'itsm.service_management', 'View the KPI dashboard', 'SLA and service KPIs', true, false, 6),
    ('itsm.service_management.cmdb_view', 'itsm.service_management', 'View configuration items', 'Environments, repositories, versions and owners', true, false, 7),
    ('itsm.service_management.cmdb_edit', 'itsm.service_management', 'Edit configuration items', 'Correct owner or notes', true, false, 8),
    ('itsm.reports.reports_view', 'itsm.reports', 'View reports', 'Read published reports', true, false, 1),
    ('itsm.reports.reports_generate', 'itsm.reports', 'Generate reports', 'Produce a report for a period', true, false, 2),
    ('itsm.reports.reports_signoff', 'itsm.reports', 'Sign off a report', 'Accept a report; it is then frozen', true, false, 3),
    ('itsm.notifications.notifications_view', 'itsm.notifications', 'View notifications', 'In-portal notifications', true, false, 1),
    ('itsm.notifications.notifications_preferences', 'itsm.notifications', 'Set notification preferences', 'Choose what you receive by email and Teams', true, false, 2),
    ('automation.deployments_and_environments.deployments_start', 'automation.deployments_and_environments', 'Start a rollout', 'Start a plugin rollout once approved', true, false, 1),
    ('automation.deployments_and_environments.deployments_rollback', 'automation.deployments_and_environments', 'Roll back', 'Roll back a rollout', true, false, 2),
    ('automation.deployments_and_environments.environments_view', 'automation.deployments_and_environments', 'View environments', 'Env-1, Env-2 and provisioned environments', true, false, 3),
    ('automation.deployments_and_environments.environments_versions', 'automation.deployments_and_environments', 'View deployed versions', 'Artifactory and Firewall plugin versions per environment', true, false, 4),
    ('automation.deployments_and_environments.environments_request', 'automation.deployments_and_environments', 'Request an environment', 'Request a provisioned environment', true, false, 5),
    ('automation.deployments_and_environments.environments_extend', 'automation.deployments_and_environments', 'Extend an environment''s time to live', 'Extend within the maximum, with a ticket', true, false, 6),
    ('automation.deployments_and_environments.environments_manage', 'automation.deployments_and_environments', 'Tear down an environment', 'Remove an environment now', true, false, 7),
    ('automation.deployments_and_environments.bundles_view', 'automation.deployments_and_environments', 'View plugin bundles', 'Available bundle versions and checksums', true, false, 8),
    ('automation.testing_and_evidence.tests_run', 'automation.testing_and_evidence', 'Run tests', 'Start a test run', true, false, 1),
    ('automation.testing_and_evidence.tests_retest', 'automation.testing_and_evidence', 'Retest', 'Run again after a blocker is resolved', true, false, 2),
    ('automation.testing_and_evidence.tests_view', 'automation.testing_and_evidence', 'View test runs and results', 'Results per repository, format and mode', true, false, 3),
    ('automation.testing_and_evidence.evidence_view', 'automation.testing_and_evidence', 'View evidence', 'Logs, responses and screenshots', true, false, 4),
    ('automation.testing_and_evidence.blockers_view', 'automation.testing_and_evidence', 'View blockers', 'Issues that stop progress', true, false, 5),
    ('automation.testing_and_evidence.blockers_manage', 'automation.testing_and_evidence', 'Raise and manage blockers', 'Classify, assign, resolve', true, false, 6),
    ('automation.status.status_overview', 'automation.status', 'Lab status overview', 'Health of each component and environment', true, false, 1),
    ('automation.status.status_deployments', 'automation.status', 'Deployment status', 'Running and recent rollouts', true, false, 2),
    ('automation.status.operator_links', 'automation.status', 'Operator dashboard links', 'Links to the operator dashboards', true, false, 3);

-- ---------------------------------------------------------------- the packages
-- Proposals: names, prices and limits are the project owner's to set. No limits are seeded, so every limit reads as unlimited.
INSERT INTO packages (key, version, name, type, status, is_public, price, description) VALUES
    ('lab-full', 1, 'Managed Lab', 'Managed service', 'active', true, '', 'ITSM and AWS Automation: what the Managed Lab for Sonatype uses'),
    ('itsm', 1, 'ITSM only', 'Managed service', 'active', true, '', 'Ticketing and service management only'),
    ('automation', 1, 'AWS Automation only', 'Managed service', 'active', true, '', 'Environments, deployments and status only');

INSERT INTO package_products (pkg_key, pkg_version, product_key) VALUES
    ('lab-full', 1, 'itsm'), ('lab-full', 1, 'automation'), ('itsm', 1, 'itsm'), ('automation', 1, 'automation');

INSERT INTO package_groups (pkg_key, pkg_version, group_key)
SELECT p.pkg_key, p.pkg_version, g.key FROM package_products p JOIN feature_groups g ON g.product_key = p.product_key;

-- A tenant's package must exist. New tenants start on the Managed Lab package: a deliberate default for this lab, not a rule for every deployment.
ALTER TABLE tenants ADD CONSTRAINT tenants_package_exists FOREIGN KEY (package_key, package_version) REFERENCES packages (key, version);

-- ---------------------------------------------------------------- the platform's own audit trail
-- What operators and the platform itself did, across tenants (a package published, an override granted, a tenant deactivated). A tenant's own
-- audit log still receives the events that concern it; this is the operator's view of everything. Append-only, and chained like the tenant trail:
-- each row carries the hash of the one before it, so a changed or removed row is found by verification.
CREATE TABLE platform_audit (
    seq         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    actor       TEXT NOT NULL,
    action      TEXT NOT NULL,
    tenant_id   UUID,
    tenant_name TEXT,
    detail      TEXT NOT NULL DEFAULT '',
    prev_hash   TEXT NOT NULL,
    hash        TEXT NOT NULL,
    CONSTRAINT platform_audit_action CHECK (action ~ '^[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)+$')
);
CREATE INDEX idx_platform_audit_tenant ON platform_audit (tenant_id, seq DESC);

CREATE FUNCTION platform_audit_append_only() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'platform_audit is append-only (% refused)', TG_OP USING ERRCODE = 'insufficient_privilege';
END $$;
CREATE TRIGGER platform_audit_no_change BEFORE UPDATE OR DELETE ON platform_audit FOR EACH ROW EXECUTE FUNCTION platform_audit_append_only();
CREATE TRIGGER platform_audit_no_truncate BEFORE TRUNCATE ON platform_audit FOR EACH STATEMENT EXECUTE FUNCTION platform_audit_append_only();
