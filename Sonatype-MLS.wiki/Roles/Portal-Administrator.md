# Portal Administrator

Manage users and roles, decide what each role can do, switch features on and off, keep the audit record, and review access. An administrator cannot approve their own change.

[[_TOC_]]

**Status legend.** **Built** = running and used. **Partly** = exists, with a stated gap. **Not built** = designed, nothing runs. A plan is never described as behaviour.

**Who:** Named people who run the portal itself.

**Sign-in:** single sign-on with MFA. What you see depends on the features the portal administrator has assigned to this role and switched on; if a feature below is missing, it has been switched off or not assigned.

## Manage users

1. Open **Administration**, then **Users**.
2. Invite or disable a user, and assign roles and groups.
3. Onboarding is requested through an **access ticket**; the target is one business day, and revocation four hours.

## Manage groups

1. Open **Administration**, then **Groups**.
2. Create a group, set its members and its roles. Every member gets the group's roles, and a group can also give a team reach to environments.

## Decide what each role can do

1. Open **Administration**, then **Roles**, then choose a role, or create a custom role.
2. Tick a **feature group** (all its scopes) or **individual scopes (features)**. Built-in roles cannot be deleted or renamed.
3. Save. The portal checks the guard rails (see [Role Feature Assignment](/Feature-Flags/Role-Feature-Assignment)) and refuses a combination that breaks them.
4. The change applies on the next request. Where a feature is **sensitive**, a second administrator confirms it (proposed).

## Turn a feature on or off

Open **Administration**, then **Features**, find the feature, and switch its flag. A feature that is off is unavailable to every role, and the server refuses it. See [Feature Flags](/Feature-Flags).

## Keep the record

- **Audit** lets you search events and verify the chain.
- **Access review** is done quarterly: confirm or remove each user's roles.
- **Settings**, **SLA policies** and **notification templates** are managed here.

## Break-glass

Emergency access is a separate logged role, not part of this one.

## Features of this role

Default assignment. The portal administrator can change it ([Role Feature Assignment](/Feature-Flags/Role-Feature-Assignment)). Feature definitions are in the [Feature Catalogue](/Feature-Flags/Feature-Catalogue).

| Group | Scope (feature) | What it lets you do | Status |
|---|---|---|---|
| Tickets | `tickets.create_access` | Create an access ticket: Ask to join, change or leave (joiner, mover, leaver) | Not built |
| Tickets | `tickets.view_own` | View own tickets: See tickets you raised or follow | Not built |
| Tickets | `tickets.view_all` | View all tickets: See every ticket in your organisation scope | Not built |
| Tickets | `tickets.comment` | Comment on a ticket: Add information or answer a question | Not built |
| Tickets | `tickets.watch` | Follow a ticket: Get notified of every change | Not built |
| Approvals | `approvals.history` | See approval history: Who decided what and when, on tickets you can see | Not built |
| Deployments and environments | `environments.view` | View environments: Env-1, Env-2 and provisioned environments | Not built |
| Deployments and environments | `environments.versions` | View deployed versions: Artifactory and Firewall plugin versions per environment | Not built |
| Testing and evidence | `tests.view` | View test runs and results: Results per repository, format and mode | Not built |
| Testing and evidence | `evidence.view` | View evidence: Logs, responses and screenshots | Not built |
| Testing and evidence | `blockers.view` | View blockers: Issues that stop progress | Not built |
| Service management | `incidents.view` | View incidents: Open and recent incidents | Not built |
| Service management | `sla.view` | View SLA clocks: Time left and breaches on tickets you can see | Not built |
| Service management | `kpi.view` | View the KPI dashboard: SLA and service KPIs | Not built |
| Service management | `cmdb.view` | View configuration items: Environments, repositories, versions and owners | Not built |
| Reports | `reports.view` | View reports: Read published reports | Not built |
| Status | `status.overview` | Lab status overview: Health of each component and environment | Not built |
| Status | `status.deployments` | Deployment status: Running and recent rollouts | Not built |
| Status | `operator.links` | Operator dashboard links: Links to the operator dashboards | Not built |
| Notifications | `notifications.view` | View notifications: In-portal notifications | Not built |
| Notifications | `notifications.preferences` | Set notification preferences: Choose what you receive by email and Teams | Not built |
| Audit and access review | `audit.view` | Search the audit log: Search audit events (sensitive) | Not built |
| Audit and access review | `audit.verify` | Verify the audit chain: Check that the audit record is intact | Not built |
| Audit and access review | `access.recertify` | Recertify access: Quarterly review of roles and accounts (sensitive) | Not built |
| Administration | `users.read` | View users: See people, their roles and groups | Not built |
| Administration | `users.write` | Invite and disable users: Invite a person, enable or disable them (sensitive) | Not built |
| Administration | `groups.read` | View groups: See groups, members and roles | Not built |
| Administration | `groups.write` | Manage groups: Create and delete groups, set members, and give a group reach to environments (sensitive) | Not built |
| Administration | `roles.read` | View roles and scopes: See roles and the scope catalogue | Not built |
| Administration | `roles.write` | Manage roles and their scopes: Create custom roles, decide which scopes each role holds, set a person's and a group's roles (sensitive) | Not built |
| Administration | `sso.manage` | Manage single sign-on: Connect and change the identity-provider federation (sensitive) | Not built |
| Administration | `flags.manage` | Turn features on and off: Switch a scope's flag (sensitive) | Not built |
| Administration | `settings.manage` | Manage portal settings: Portal-wide settings | Not built |
| Administration | `sla.policies.manage` | Manage SLA policies: Clocks and targets | Not built |
| Administration | `notifications.templates` | Manage notification templates: Wording of messages | Not built |

## What this role cannot do

- Approve work, including your own change (the approval features are not assignable to this role).
- Delete or edit an audit event.
- Assign yourself a feature that breaks a guard rail.

## Notifications

You are told when: a feature change awaits your confirmation; access recertification is due; the audit chain check fails; a sensitive setting changes.

## Related

[Roles](/Roles), [Feature Flags](/Feature-Flags), [Audit and Logging](/Architecture/Audit-and-Logging).
