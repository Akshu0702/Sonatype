# Feature Catalogue

> **Superseded as the model; kept for the permission list.** Features are now grouped under Products and entitled through packages: see [Products and Features](/Packages-and-Feature-Flags/Products-and-Features) and the [Entitlement Model](/Packages-and-Feature-Flags/Entitlement-Model).

Every feature, grouped. A feature has a stable id, a name, what it lets a person do, and whether it is sensitive. The flag state of every feature is **Off** until its story is verified.

[[_TOC_]]

**Status legend.** **Built** = running and used. **Partly** = exists, with a stated gap. **Not built** = designed, nothing runs. A plan is never described as behaviour.

**Sensitive** features can be given only within the guard rails and, where decided, need a second administrator to confirm a change.

## Tickets

Raise work, follow it and keep it moving.

| Feature id | Name | What it lets you do | Sensitive | Status |
|---|---|---|---|---|
| `tickets.create_support` | Create a support ticket | Ask a question or report a problem with the lab | No | Not built |
| `tickets.create_provisioning` | Create a provisioning ticket | Ask for a test environment | No | Not built |
| `tickets.create_change` | Create a plugin or environment change ticket | Ask for a plugin deployment, upgrade or environment change, with the build file | No | Not built |
| `tickets.create_incident` | Create an incident ticket | Report something that is broken | No | Not built |
| `tickets.create_problem` | Create a problem ticket | Record the underlying cause of repeated incidents | No | Not built |
| `tickets.create_access` | Create an access ticket | Ask to join, change or leave (joiner, mover, leaver) | No | Not built |
| `tickets.view_own` | View own tickets | See tickets you raised or follow | No | Not built |
| `tickets.view_all` | View all tickets | See every ticket in your organisation scope | No | Not built |
| `tickets.update` | Edit a ticket | Change description and fields while the ticket is open | No | Not built |
| `tickets.comment` | Comment on a ticket | Add information or answer a question | No | Not built |
| `tickets.attach` | Attach files | Upload a build ZIP or supporting file | No | Not built |
| `tickets.watch` | Follow a ticket | Get notified of every change | No | Not built |
| `tickets.chase` | Ask for an update | Send a reminder to the assignee without opening a new ticket | No | Not built |
| `tickets.escalate` | Escalate a ticket | Raise it along the escalation path when the SLA is at risk | No | Not built |
| `tickets.cancel` | Cancel a ticket | Withdraw your own ticket before work starts | No | Not built |
| `tickets.reopen` | Reopen a ticket | Reopen a closed ticket within the allowed window | No | Not built |
| `tickets.assign` | Assign a ticket | Assign or reassign to a person | No | Not built |
| `tickets.transition` | Move a ticket between states | Manual transitions that are not automatic | No | Not built |

## Approvals

Decide whether work may proceed.

| Feature id | Name | What it lets you do | Sensitive | Status |
|---|---|---|---|---|
| `approvals.queue` | See the approval queue | Tickets waiting for a decision | No | Not built |
| `approvals.decide` | Approve or reject | Decide a deploy or restart, with a reason | Yes | Not built |
| `approvals.history` | See approval history | Who decided what and when, on tickets you can see | No | Not built |

## Deployments and environments

See and operate the lab environments.

| Feature id | Name | What it lets you do | Sensitive | Status |
|---|---|---|---|---|
| `deployments.start` | Start a rollout | Start a plugin rollout once approved | Yes | Not built |
| `deployments.rollback` | Roll back | Roll back a rollout | Yes | Not built |
| `environments.view` | View environments | Env-1, Env-2 and provisioned environments | No | Not built |
| `environments.versions` | View deployed versions | Artifactory and Firewall plugin versions per environment | No | Not built |
| `environments.request` | Request an environment | Request a provisioned environment | No | Not built |
| `environments.extend` | Extend an environment's time to live | Extend within the maximum, with a ticket | No | Not built |
| `environments.manage` | Tear down an environment | Remove an environment now | Yes | Not built |
| `bundles.view` | View plugin bundles | Available bundle versions and checksums | No | Not built |

## Testing and evidence

Run the Firewall tests and keep the proof.

| Feature id | Name | What it lets you do | Sensitive | Status |
|---|---|---|---|---|
| `tests.run` | Run tests | Start a test run | No | Not built |
| `tests.retest` | Retest | Run again after a blocker is resolved | No | Not built |
| `tests.view` | View test runs and results | Results per repository, format and mode | No | Not built |
| `evidence.view` | View evidence | Logs, responses and screenshots | No | Not built |
| `blockers.view` | View blockers | Issues that stop progress | No | Not built |
| `blockers.manage` | Raise and manage blockers | Classify, assign, resolve | No | Not built |

## Service management

Changes, incidents, problems, SLAs and the configuration view.

| Feature id | Name | What it lets you do | Sensitive | Status |
|---|---|---|---|---|
| `changes.manage` | Manage change records | Risk, rollback plan and window | No | Not built |
| `incidents.view` | View incidents | Open and recent incidents | No | Not built |
| `incidents.manage` | Manage incidents | Triage, update and resolve | No | Not built |
| `problems.manage` | Manage problems | Root cause and linked incidents | No | Not built |
| `sla.view` | View SLA clocks | Time left and breaches on tickets you can see | No | Not built |
| `kpi.view` | View the KPI dashboard | SLA and service KPIs | No | Not built |
| `cmdb.view` | View configuration items | Environments, repositories, versions and owners | No | Not built |
| `cmdb.edit` | Edit configuration items | Correct owner or notes | No | Not built |

## Reports

Weekly and monthly reports.

| Feature id | Name | What it lets you do | Sensitive | Status |
|---|---|---|---|---|
| `reports.view` | View reports | Read published reports | No | Not built |
| `reports.generate` | Generate reports | Produce a report for a period | No | Not built |
| `reports.signoff` | Sign off a report | Accept a report; it is then frozen | Yes | Not built |

## Status

Is the lab healthy and what is deploying.

| Feature id | Name | What it lets you do | Sensitive | Status |
|---|---|---|---|---|
| `status.overview` | Lab status overview | Health of each component and environment | No | Not built |
| `status.deployments` | Deployment status | Running and recent rollouts | No | Not built |
| `operator.links` | Operator dashboard links | Links to the operator dashboards | No | Not built |

## Notifications

Messages from the portal.

| Feature id | Name | What it lets you do | Sensitive | Status |
|---|---|---|---|---|
| `notifications.view` | View notifications | In-portal notifications | No | Not built |
| `notifications.preferences` | Set notification preferences | Choose what you receive by email and Teams | No | Not built |

## Audit and access review

Who did what, and who may do what.

| Feature id | Name | What it lets you do | Sensitive | Status |
|---|---|---|---|---|
| `audit.view` | Search the audit log | Search audit events | Yes | Not built |
| `audit.verify` | Verify the audit chain | Check that the audit record is intact | No | Not built |
| `access.recertify` | Recertify access | Quarterly review of roles and accounts | Yes | Not built |

## Administration

Run the portal itself: people, groups, roles and scopes.

| Feature id | Name | What it lets you do | Sensitive | Status |
|---|---|---|---|---|
| `users.read` | View users | See people, their roles and groups | No | Not built |
| `users.write` | Invite and disable users | Invite a person, enable or disable them | Yes | Not built |
| `groups.read` | View groups | See groups, members and roles | No | Not built |
| `groups.write` | Manage groups | Create and delete groups, set members, and give a group reach to environments | Yes | Not built |
| `roles.read` | View roles and scopes | See roles and the scope catalogue | No | Not built |
| `roles.write` | Manage roles and their scopes | Create custom roles, decide which scopes each role holds, set a person's and a group's roles | Yes | Not built |
| `sso.manage` | Manage single sign-on | Connect and change the identity-provider federation | Yes | Not built |
| `flags.manage` | Turn features on and off | Switch a scope's flag | Yes | Not built |
| `settings.manage` | Manage portal settings | Portal-wide settings | No | Not built |
| `sla.policies.manage` | Manage SLA policies | Clocks and targets | No | Not built |
| `notifications.templates` | Manage notification templates | Wording of messages | No | Not built |

65 features in 10 groups.

## Related

[Feature Flags](/Feature-Flags), [Role Feature Assignment](/Feature-Flags/Role-Feature-Assignment), [Roles](/Roles).
