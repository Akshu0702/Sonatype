# Data Model

The portal database. This is the design; the schema is reconciled with the prototype before the first migration.

[[_TOC_]]

**Status legend.** **Built** = running and used. **Partly** = exists, with a stated gap. **Not built** = designed, nothing runs. A plan is never described as behaviour.

**State: Not built** (a prototype exists with its own schema that has not been reviewed against this design).

## Conventions

- PostgreSQL, one database for the portal. Keycloak has its own.
- Table and column names in `snake_case`; primary keys are UUIDs; timestamps are `timestamptz` in UTC.
- Every table has `created_at`; mutable tables also have `updated_at`.
- Row-level security is enabled and forced on tables that hold ticket data. The policy keys on the organisation of the caller, set per transaction. With no organisation bound, no rows are returned.
- Migrations are forward-only SQL files in `mls-portal-api/migrations`, named `V<n>__<description>.sql`. Each must run safely beside the previous application version.
- Nothing is deleted in place for audit-relevant data; status fields close records.

## Entity relationships

::: mermaid
erDiagram
  USERS ||--o{ USER_ROLES : holds
  ROLES ||--o{ USER_ROLES : given_to
  USERS ||--o{ GROUP_MEMBERS : in
  GROUPS ||--o{ GROUP_MEMBERS : has
  GROUPS ||--o{ GROUP_ROLES : carries
  ROLES ||--o{ GROUP_ROLES : given_to
  ROLES ||--o{ ROLE_SCOPES : holds
  SCOPES ||--o{ ROLE_SCOPES : in
  SCOPES ||--|| SCOPE_FLAG : switched_by
  GROUPS ||--o{ GROUP_ENVIRONMENTS : reaches
  ENVIRONMENT ||--o{ GROUP_ENVIRONMENTS : reached_by
  USERS ||--o{ TICKET : requests
  TICKET ||--o{ TICKET_EVENT : records
  TICKET ||--o{ ATTACHMENT : holds
  TICKET ||--o{ APPROVAL : needs
  TICKET ||--o{ DEPLOYMENT : triggers
  TICKET ||--o{ TEST_RUN : runs
  TICKET ||--o{ BLOCKER : raises
  TICKET ||--o| SLA_CLOCK : timed_by
  TICKET ||--o| CHANGE_RECORD : may_be
  ENVIRONMENT ||--o{ DEPLOYMENT : receives
  PLUGIN_BUNDLE ||--o{ DEPLOYMENT : delivers
  CONFIGURATION ||--o{ ENVIRONMENT : defines
  TEST_RUN ||--o{ TEST_RESULT : contains
  TEST_RESULT }o--o| EVIDENCE : cites
  ENVIRONMENT ||--o{ CI_ITEM : described_by
  USERS ||--o{ AUDIT_EVENT : acts
:::

## Tables

### Identity and access (users, groups, roles, scopes)

The model is adopted from the Baton project: a user holds roles directly and through groups; a role holds scopes; a scope is one thing a person may do. Baton's wiki calls the catalogue permissions. Each scope is also a feature with a flag ([Feature Flags](/Feature-Flags)). A person's scopes are the union of their own roles and their groups' roles, limited to scopes whose flag is on. See [Authentication and Authorization](/Architecture/Authentication-and-Authorization).

| Table | Key columns | Notes |
|---|---|---|
| `users` | `id`, `email`, `display_name`, `idp_subject`, `org`, `status`, `last_sign_in_at`, `created_at` | One row per person, linked to the sign-in subject. `org` is Sonatype or Motivity. Never holds a credential. `status` is active or disabled |
| `groups` | `id`, `name`, `description`, `created_at` | A named set of users |
| `roles` | `id`, `name`, `description`, `system`, `created_at` | `system` marks the built-in roles, which cannot be deleted or renamed. Custom roles have `system` false. Names are unique ignoring case |
| `scopes` | `key`, `scope_group`, `name`, `description`, `sensitive` | The scope catalogue, for example `tickets.create_support`. Fixed in code and mirrored by migration; a startup check fails if they differ |
| `scope_flag` | `scope_key`, `state`, `changed_by`, `changed_at` | On or off. A new scope starts off |
| `role_scopes` | `role_id`, `scope_key`, `granted_by`, `granted_at` | The scopes of each role. This is the only way a scope is given |
| `user_roles` | `user_id`, `role_id`, `granted_by`, `granted_at` | Direct roles |
| `group_members` | `group_id`, `user_id` | Membership |
| `group_roles` | `group_id`, `role_id` | Roles carried by a group |
| `group_environments` | `group_id`, `environment_id`, `level` | Reach: the environments a group can see and operate |
| `scope_conflict` | `scope_a`, `scope_b`, `reason` | Pairs that cannot be held by the same person (separation of duties) |
| `scope_change` | `id`, `kind`, `target`, `requested_by`, `confirmed_by`, `state`, `requested_at`, `decided_at` | Pending and decided changes to sensitive scopes, for second-administrator confirmation (proposed) |
| `auth_provider_config` | `id`, `alias`, `protocol`, `issuer_or_metadata`, `client_id`, `secret_ref`, `trust_email`, `status` | Identity-provider federation. The secret is a reference to the secret store, never a value |

Rules:

- A person's scopes = scopes of their own roles ∪ scopes of the roles of all their groups, minus scopes whose flag is off.
- A role keeps its name; a built-in role cannot be deleted. A custom role needs at least one scope.
- The `scopes` catalogue is the only place scopes are defined. No second table of switches or rights exists.
- Roles are never read from the sign-in token.
- Every change to users, groups, roles, scopes and flags is an audit event.

### Tickets and workflow

| Table | Key columns | Notes |
|---|---|---|
| `ticket` | `id`, `number`, `type`, `title`, `description`, `status`, `priority`, `requester_id`, `assignee_id`, `assignee_group_id`, `environment_id`, `created_at`, `due_at`, `closed_at` | Types: provisioning, support, plugin or environment change, incident, problem, access |
| `ticket_event` | `id`, `ticket_id`, `event_type`, `actor_id`, `payload`, `created_at` | The ticket's history |
| `attachment` | `id`, `ticket_id`, `kind`, `s3_key`, `sha256`, `size_bytes`, `uploaded_by` | The file lives in S3; the row holds its address and checksum |
| `approval` | `id`, `ticket_id`, `stage`, `decision`, `decided_by`, `reason`, `decided_at` | A decider cannot be the requester |
| `blocker` | `id`, `ticket_id`, `class`, `severity`, `owner_id`, `status`, `raised_at`, `resolved_at` | Classified, routed, escalated |

### Environments and deployments

| Table | Key columns | Notes |
|---|---|---|
| `configuration` | `id`, `code`, `os_image`, `db_engine`, `db_version`, `description` | The priority configurations |
| `environment` | `id`, `kind`, `name`, `owner_group_id`, `status`, `account`, `configuration_id`, `ticket_id`, `ttl_expires_at`, `created_at` | Lab environments and provisioned ones; the registry behind the CMDB view |
| `plugin_bundle` | `id`, `version`, `sha256`, `s3_key`, `source_ref`, `created_at` | Immutable once published |
| `deployment` | `id`, `ticket_id`, `environment_id`, `plugin_bundle_id`, `execution_arn`, `status`, `started_at`, `finished_at`, `rolled_back` | One per rollout; links to the workflow execution |

### Testing and evidence

| Table | Key columns | Notes |
|---|---|---|
| `test_run` | `id`, `ticket_id`, `deployment_id`, `status`, `started_at`, `finished_at` | A run of the matrix |
| `test_result` | `id`, `test_run_id`, `case_id`, `repository`, `format`, `mode`, `expected`, `actual`, `passed`, `evidence_id` | One row per matrix case |
| `evidence` | `id`, `ticket_id`, `kind`, `s3_key`, `sha256`, `created_at` | Logs, responses, screenshots |

### Service management

| Table | Key columns | Notes |
|---|---|---|
| `change_record` | `id`, `ticket_id`, `risk`, `rollback_plan`, `window_start`, `window_end`, `status` | Required for plugin and environment changes |
| `incident` | `id`, `source`, `severity`, `status`, `ticket_id`, `opened_at`, `resolved_at` | Opened by alarms or people |
| `problem` | `id`, `title`, `root_cause`, `status` | Linked to incidents |
| `sla_policy` | `id`, `priority`, `response_minutes`, `resolution_minutes`, `calendar` | Business hours 11 AM to 7 PM IST |
| `sla_clock` | `id`, `ticket_id`, `policy_id`, `state`, `started_at`, `paused_ms`, `breach_at` | Runs, pauses and breaches |
| `ci_item` | `id`, `kind`, `name`, `version`, `environment_id`, `owner_team` | CMDB-lite |
| `report` | `id`, `kind`, `period_start`, `period_end`, `status`, `s3_key`, `signed_by`, `signed_at` | Weekly and monthly reports; frozen once signed |
| `notification` | `id`, `channel`, `recipient`, `template`, `ticket_id`, `status`, `sent_at` | Email and Teams |

### Audit

`audit_event` is described in [Audit and Logging](/Architecture/Audit-and-Logging).

## Retention

| Data | Rule |
|---|---|
| Tickets, approvals, evidence rows | Kept for the engagement and its managed-service term |
| Audit events | Per [Audit and Logging](/Architecture/Audit-and-Logging) |
| Evidence files in S3 | Versioned; lifecycle rules set when the retention period is agreed |

## Related

[API Reference](/API-Reference), [Services](/Services).
