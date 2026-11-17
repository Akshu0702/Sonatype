# Products and Features

The catalogue of what can be switched on for a tenant: **Products**, the **feature groups** in each, and the **features** in each group. Status: **built in the tenants service** (migration V4 seeds exactly these 65 features; the Product key for AWS Automation is `automation`, because a key part cannot contain a hyphen). The grouping is still a proposal for the project owner to confirm.

The key of a feature is `<product>.<group>.<name>` in lower case. The permission (scope) a role needs to use a feature is listed in the last column; a person can use a feature only when **both** the role grants the scope **and** the feature is live for their tenant.

## Platform (always on, not a Product a tenant selects)

Product key: `platform`

### Group: Administration

| Feature key | What it allows | Scope |
|---|---|---|
| `platform.administration.users_read` | View users | `users.read` |
| `platform.administration.users_write` | Invite and disable users | `users.write` |
| `platform.administration.groups_read` | View groups | `groups.read` |
| `platform.administration.groups_write` | Manage groups | `groups.write` |
| `platform.administration.roles_read` | View roles and scopes | `roles.read` |
| `platform.administration.roles_write` | Manage roles and their scopes | `roles.write` |
| `platform.administration.sso_manage` | Manage single sign-on | `sso.manage` |
| `platform.administration.flags_manage` | Turn features on and off | `flags.manage` |
| `platform.administration.settings_manage` | Manage portal settings | `settings.manage` |
| `platform.administration.sla_policies_manage` | Manage SLA policies | `sla.policies.manage` |
| `platform.administration.notifications_templates` | Manage notification templates | `notifications.templates` |

### Group: Audit and access review

| Feature key | What it allows | Scope |
|---|---|---|
| `platform.audit_and_access_review.audit_view` | Search the audit log | `audit.view` |
| `platform.audit_and_access_review.audit_verify` | Verify the audit chain | `audit.verify` |
| `platform.audit_and_access_review.access_recertify` | Recertify access | `access.recertify` |

## ITSM

Product key: `itsm`

### Group: Tickets

| Feature key | What it allows | Scope |
|---|---|---|
| `itsm.tickets.tickets_create_support` | Create a support ticket | `tickets.create_support` |
| `itsm.tickets.tickets_create_provisioning` | Create a provisioning ticket | `tickets.create_provisioning` |
| `itsm.tickets.tickets_create_change` | Create a plugin or environment change ticket | `tickets.create_change` |
| `itsm.tickets.tickets_create_incident` | Create an incident ticket | `tickets.create_incident` |
| `itsm.tickets.tickets_create_problem` | Create a problem ticket | `tickets.create_problem` |
| `itsm.tickets.tickets_create_access` | Create an access ticket | `tickets.create_access` |
| `itsm.tickets.tickets_view_own` | View own tickets | `tickets.view_own` |
| `itsm.tickets.tickets_view_all` | View all tickets | `tickets.view_all` |
| `itsm.tickets.tickets_update` | Edit a ticket | `tickets.update` |
| `itsm.tickets.tickets_comment` | Comment on a ticket | `tickets.comment` |
| `itsm.tickets.tickets_attach` | Attach files | `tickets.attach` |
| `itsm.tickets.tickets_watch` | Follow a ticket | `tickets.watch` |
| `itsm.tickets.tickets_chase` | Ask for an update | `tickets.chase` |
| `itsm.tickets.tickets_escalate` | Escalate a ticket | `tickets.escalate` |
| `itsm.tickets.tickets_cancel` | Cancel a ticket | `tickets.cancel` |
| `itsm.tickets.tickets_reopen` | Reopen a ticket | `tickets.reopen` |
| `itsm.tickets.tickets_assign` | Assign a ticket | `tickets.assign` |
| `itsm.tickets.tickets_transition` | Move a ticket between states | `tickets.transition` |

### Group: Approvals

| Feature key | What it allows | Scope |
|---|---|---|
| `itsm.approvals.approvals_queue` | See the approval queue | `approvals.queue` |
| `itsm.approvals.approvals_decide` | Approve or reject | `approvals.decide` |
| `itsm.approvals.approvals_history` | See approval history | `approvals.history` |

### Group: Service management

| Feature key | What it allows | Scope |
|---|---|---|
| `itsm.service_management.changes_manage` | Manage change records | `changes.manage` |
| `itsm.service_management.incidents_view` | View incidents | `incidents.view` |
| `itsm.service_management.incidents_manage` | Manage incidents | `incidents.manage` |
| `itsm.service_management.problems_manage` | Manage problems | `problems.manage` |
| `itsm.service_management.sla_view` | View SLA clocks | `sla.view` |
| `itsm.service_management.kpi_view` | View the KPI dashboard | `kpi.view` |
| `itsm.service_management.cmdb_view` | View configuration items | `cmdb.view` |
| `itsm.service_management.cmdb_edit` | Edit configuration items | `cmdb.edit` |

### Group: Reports

| Feature key | What it allows | Scope |
|---|---|---|
| `itsm.reports.reports_view` | View reports | `reports.view` |
| `itsm.reports.reports_generate` | Generate reports | `reports.generate` |
| `itsm.reports.reports_signoff` | Sign off a report | `reports.signoff` |

### Group: Notifications

| Feature key | What it allows | Scope |
|---|---|---|
| `itsm.notifications.notifications_view` | View notifications | `notifications.view` |
| `itsm.notifications.notifications_preferences` | Set notification preferences | `notifications.preferences` |

## AWS Automation

Product key: `automation`

### Group: Deployments and environments

| Feature key | What it allows | Scope |
|---|---|---|
| `automation.deployments_and_environments.deployments_start` | Start a rollout | `deployments.start` |
| `automation.deployments_and_environments.deployments_rollback` | Roll back | `deployments.rollback` |
| `automation.deployments_and_environments.environments_view` | View environments | `environments.view` |
| `automation.deployments_and_environments.environments_versions` | View deployed versions | `environments.versions` |
| `automation.deployments_and_environments.environments_request` | Request an environment | `environments.request` |
| `automation.deployments_and_environments.environments_extend` | Extend an environment's time to live | `environments.extend` |
| `automation.deployments_and_environments.environments_manage` | Tear down an environment | `environments.manage` |
| `automation.deployments_and_environments.bundles_view` | View plugin bundles | `bundles.view` |

### Group: Testing and evidence

| Feature key | What it allows | Scope |
|---|---|---|
| `automation.testing_and_evidence.tests_run` | Run tests | `tests.run` |
| `automation.testing_and_evidence.tests_retest` | Retest | `tests.retest` |
| `automation.testing_and_evidence.tests_view` | View test runs and results | `tests.view` |
| `automation.testing_and_evidence.evidence_view` | View evidence | `evidence.view` |
| `automation.testing_and_evidence.blockers_view` | View blockers | `blockers.view` |
| `automation.testing_and_evidence.blockers_manage` | Raise and manage blockers | `blockers.manage` |

### Group: Status

| Feature key | What it allows | Scope |
|---|---|---|
| `automation.status.status_overview` | Lab status overview | `status.overview` |
| `automation.status.status_deployments` | Deployment status | `status.deployments` |
| `automation.status.operator_links` | Operator dashboard links | `operator.links` |

## Rules

- **Platform** features are mandatory: always on, cannot be overridden, and are not part of any package.
- A group named *mandatory* in a Product is always on whenever the Product is in the tenant's package.
- A feature **needs** at most one other feature of the same Product; the dependency is declared in the catalogue, not re-checked in code.
- New features default to **off** and are unreachable until a package grants them.
- 65 features are catalogued today.

## Related

[Entitlement Model](/Packages-and-Feature-Flags/Entitlement-Model), [Packages](/Packages-and-Feature-Flags/Packages), [Adding a Feature Flag](/Packages-and-Feature-Flags/Adding-a-Feature-Flag).
