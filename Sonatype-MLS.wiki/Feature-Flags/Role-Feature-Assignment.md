# Role Feature Assignment

How the portal administrator decides what each role can do, the default starting point, and the guard rails.

[[_TOC_]]

**Status legend.** **Built** = running and used. **Partly** = exists, with a stated gap. **Not built** = designed, nothing runs. A plan is never described as behaviour.

## How an administrator assigns features

1. Administration, Roles, choose a role, or create a custom role.
2. Tick a **group** to give every scope in it, or tick **individual scopes**.
3. Review the summary of what changes for the people who hold the role, directly or through a group.
4. Save. The portal runs the guard rails and refuses an invalid combination with a reason.
5. For a sensitive scope, a second administrator confirms the change (proposed).
6. The change is audited and applies within seconds.

People get roles in two ways: directly (Administration, Users) or through a group (Administration, Groups). To see the effect for one person, open the user: the page shows their effective scopes, which are the union of their roles and their groups' roles, limited to those switched on.

## Built-in and custom roles

| Kind | Rule |
|---|---|
| Built-in roles (`REQUESTER`, `APPROVER`, `TEST_ENGINEER`, `INFRA_ENGINEER`, `PORTAL_ADMIN`) | Marked `system`. They cannot be deleted or renamed. They ship with the default assignment below |
| Custom roles | Created by an administrator from the scope catalogue. Name up to 80 characters, starting with a letter or digit, unique ignoring case and not equal to a built-in name; at least one scope |

**Difference from Baton.** Baton's built-in roles cannot be edited at all. Here the administrator decides what each role can do, so the scopes of a built-in role can be adjusted within the guard rails. To avoid overwriting the administrator's choices, an upgrade adds a new scope to a built-in role's defaults only in the release that introduces it, and never re-adds a scope that was removed. This difference is recorded in [Known Issues and Gaps](/Operations/Known-Issues-and-Gaps).

## Default assignment

The starting point, proposed for Sonatype to review. The administrator can change any of it. Letters are roles: **R** Requester, **A** Approver, **T** Test engineer, **I** Infra engineer, **D** Portal administrator.

| Scope (feature) | R | A | T | I | D |
|---|---|---|---|---|---|
| **Tickets** | | | | | |
| `tickets.create_support` | Yes | Yes | Yes | Yes | . |
| `tickets.create_provisioning` | Yes | . | Yes | Yes | . |
| `tickets.create_change` | Yes | . | . | Yes | . |
| `tickets.create_incident` | Yes | . | Yes | Yes | . |
| `tickets.create_problem` | . | . | Yes | Yes | . |
| `tickets.create_access` | Yes | . | . | Yes | Yes |
| `tickets.view_own` | Yes | Yes | Yes | Yes | Yes |
| `tickets.view_all` | . | Yes | Yes | Yes | Yes |
| `tickets.update` | Yes | . | . | Yes | . |
| `tickets.comment` | Yes | Yes | Yes | Yes | Yes |
| `tickets.attach` | Yes | . | . | Yes | . |
| `tickets.watch` | Yes | Yes | Yes | Yes | Yes |
| `tickets.chase` | Yes | . | . | . | . |
| `tickets.escalate` | Yes | Yes | . | . | . |
| `tickets.cancel` | Yes | . | . | . | . |
| `tickets.reopen` | Yes | . | . | Yes | . |
| `tickets.assign` | . | . | . | Yes | . |
| `tickets.transition` | . | . | Yes | Yes | . |
| **Approvals** | | | | | |
| `approvals.queue` | . | Yes | . | . | . |
| `approvals.decide` (S) | . | Yes | . | . | . |
| `approvals.history` | Yes | Yes | Yes | Yes | Yes |
| **Deployments and environments** | | | | | |
| `deployments.start` (S) | . | . | . | Yes | . |
| `deployments.rollback` (S) | . | . | . | Yes | . |
| `environments.view` | Yes | Yes | Yes | Yes | Yes |
| `environments.versions` | Yes | Yes | Yes | Yes | Yes |
| `environments.request` | Yes | . | Yes | Yes | . |
| `environments.extend` | Yes | . | . | Yes | . |
| `environments.manage` (S) | . | . | . | Yes | . |
| `bundles.view` | . | Yes | Yes | Yes | . |
| **Testing and evidence** | | | | | |
| `tests.run` | . | . | Yes | Yes | . |
| `tests.retest` | . | . | Yes | . | . |
| `tests.view` | Yes | Yes | Yes | Yes | Yes |
| `evidence.view` | Yes | Yes | Yes | Yes | Yes |
| `blockers.view` | Yes | Yes | Yes | Yes | Yes |
| `blockers.manage` | . | . | Yes | Yes | . |
| **Service management** | | | | | |
| `changes.manage` | . | . | . | Yes | . |
| `incidents.view` | . | Yes | Yes | Yes | Yes |
| `incidents.manage` | . | . | . | Yes | . |
| `problems.manage` | . | . | . | Yes | . |
| `sla.view` | Yes | Yes | Yes | Yes | Yes |
| `kpi.view` | . | Yes | . | Yes | Yes |
| `cmdb.view` | . | Yes | Yes | Yes | Yes |
| `cmdb.edit` | . | . | . | Yes | . |
| **Reports** | | | | | |
| `reports.view` | Yes | Yes | Yes | Yes | Yes |
| `reports.generate` | . | . | Yes | Yes | . |
| `reports.signoff` (S) | . | Yes | . | . | . |
| **Status** | | | | | |
| `status.overview` | Yes | Yes | Yes | Yes | Yes |
| `status.deployments` | Yes | Yes | Yes | Yes | Yes |
| `operator.links` | . | . | Yes | Yes | Yes |
| **Notifications** | | | | | |
| `notifications.view` | Yes | Yes | Yes | Yes | Yes |
| `notifications.preferences` | Yes | Yes | Yes | Yes | Yes |
| **Audit and access review** | | | | | |
| `audit.view` (S) | . | . | . | . | Yes |
| `audit.verify` | . | . | . | . | Yes |
| `access.recertify` (S) | . | . | . | . | Yes |
| **Administration** | | | | | |
| `users.read` | . | Yes | . | Yes | Yes |
| `users.write` (S) | . | . | . | . | Yes |
| `groups.read` | . | . | . | Yes | Yes |
| `groups.write` (S) | . | . | . | . | Yes |
| `roles.read` | . | . | . | . | Yes |
| `roles.write` (S) | . | . | . | . | Yes |
| `sso.manage` (S) | . | . | . | . | Yes |
| `flags.manage` (S) | . | . | . | . | Yes |
| `settings.manage` | . | . | . | . | Yes |
| `sla.policies.manage` | . | . | . | . | Yes |
| `notifications.templates` | . | . | . | . | Yes |

(S) marks a sensitive scope. The automation service is not in this table: it has no portal role and its permissions are fixed in cloud access policies ([Automation Service](/Roles/Automation-Service)).

## Working as a role (personas)

A person who holds several roles can narrow themselves to **one** role for a session, using "Working as". The portal then uses only that role's scopes, and only if the person really holds the role (directly or through a group); a role they do not hold is ignored. This cannot widen access. It helps a person who is both a requester and an approver to act in one capacity at a time. The own-ticket rule below always applies.

## Guard rails

### Rules checked when you save

The rules are checked on a person's **effective scopes**, because roles and groups add up, and also on each role.

| Rule | Effect |
|---|---|
| `approvals.decide` cannot be held together with `users.write`, `groups.write`, `roles.write` or `flags.manage` | The save is refused. The person who administers cannot be the person who approves |
| `approvals.decide` and `deployments.start` cannot be held together | The person who approves cannot also start the rollout |
| The administrator must always keep `roles.write`, `users.write` and `flags.manage` somewhere, and at least two people must hold them | Prevents locking everyone out |
| A role cannot be given a scope that is not yet built | Refused |
| Automation permissions cannot be given to any role | Refused |

### Rules checked on every request

| Rule | Effect |
|---|---|
| Nobody can decide their own ticket, whatever scopes they hold | The server refuses, and a database check backs it |
| A requester sees only their own tickets unless they hold `tickets.view_all` | Filtered on the server |
| A scope whose flag is Off is refused for everyone | Generic refusal |
| An environment is visible and operable only to people who hold the scope **and** whose group reaches it | See [IAM](/Services/IAM) |

### Sensitive scopes

`approvals.decide`, `reports.signoff`, `deployments.start`, `deployments.rollback`, `environments.manage`, `users.write`, `groups.write`, `roles.write`, `sso.manage`, `flags.manage`, `access.recertify` and `audit.view`. Changing who holds one needs a second administrator to confirm. This two-person rule is proposed, not agreed ([Known Issues and Gaps](/Operations/Known-Issues-and-Gaps)).

## What is audited

| Event | Recorded |
|---|---|
| Flag turned on or off | Who, when, scope, old and new state |
| Scope added to or removed from a role | Who, role, scope, and who confirmed it |
| Role added to or removed from a user or a group | Who, target, role |
| Group created or deleted, members changed | Who, group, members |
| A save refused by a guard rail | Who, what, which rule |

## Related

[Feature Flags](/Feature-Flags), [Feature Catalogue](/Feature-Flags/Feature-Catalogue), [Roles](/Roles), [IAM API](/API-Reference/IAM-API), [Audit and Logging](/Architecture/Audit-and-Logging).
