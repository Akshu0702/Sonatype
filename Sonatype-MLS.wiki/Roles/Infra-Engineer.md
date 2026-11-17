# Infra Engineer

Deploy and roll back through the approved workflow, operate environments, and handle incidents and changes. There is no standing administrator access; privileged work goes through the portal, and break-glass use is logged.

[[_TOC_]]

**Status legend.** **Built** = running and used. **Partly** = exists, with a stated gap. **Not built** = designed, nothing runs. A plan is never described as behaviour.

**Who:** Motivity infrastructure and DevOps engineers who operate the lab.

**Sign-in:** single sign-on with MFA. What you see depends on the features the portal administrator has assigned to this role and switched on; if a feature below is missing, it has been switched off or not assigned.

## Run a rollout

1. Open an approved ticket, then **Deployments**, then **Start rollout**.
2. The workflow runs the pre-flight checks, bumps the plugin version, requests the sync, and verifies the version on every pod.
3. If a check fails, the rollout rolls back and a blocker is opened.
4. You can **Roll back** manually. The reason is recorded.

## Operate environments

| You want to | Do this |
|---|---|
| See health and versions | **Status** and **Environments** |
| Request or extend a provisioned environment | **Environments**, then **Request** or **Extend** |
| Remove one now | **Tear down**; the teardown evidence is attached |
| Check what is expiring | **Environments**, sorted by expiry |

## Handle incidents, problems and changes

- Alarms open **incidents** automatically. Open one, update it, and resolve it.
- Link repeated incidents to a **problem** and record the root cause.
- Record a **change** with its risk, rollback plan and window before a plugin or environment change.

## First-line work

Triage tickets, answer requests, assign tickets, and move them along. Whether first-line operations engineers need a narrower role is an open decision ([Roles](/Roles)).

## Features of this role

Default assignment. The portal administrator can change it ([Role Feature Assignment](/Feature-Flags/Role-Feature-Assignment)). Feature definitions are in the [Feature Catalogue](/Feature-Flags/Feature-Catalogue).

| Group | Scope (feature) | What it lets you do | Status |
|---|---|---|---|
| Tickets | `tickets.create_support` | Create a support ticket: Ask a question or report a problem with the lab | Not built |
| Tickets | `tickets.create_provisioning` | Create a provisioning ticket: Ask for a test environment | Not built |
| Tickets | `tickets.create_change` | Create a plugin or environment change ticket: Ask for a plugin deployment, upgrade or environment change, with the build file | Not built |
| Tickets | `tickets.create_incident` | Create an incident ticket: Report something that is broken | Not built |
| Tickets | `tickets.create_problem` | Create a problem ticket: Record the underlying cause of repeated incidents | Not built |
| Tickets | `tickets.create_access` | Create an access ticket: Ask to join, change or leave (joiner, mover, leaver) | Not built |
| Tickets | `tickets.view_own` | View own tickets: See tickets you raised or follow | Not built |
| Tickets | `tickets.view_all` | View all tickets: See every ticket in your organisation scope | Not built |
| Tickets | `tickets.update` | Edit a ticket: Change description and fields while the ticket is open | Not built |
| Tickets | `tickets.comment` | Comment on a ticket: Add information or answer a question | Not built |
| Tickets | `tickets.attach` | Attach files: Upload a build ZIP or supporting file | Not built |
| Tickets | `tickets.watch` | Follow a ticket: Get notified of every change | Not built |
| Tickets | `tickets.reopen` | Reopen a ticket: Reopen a closed ticket within the allowed window | Not built |
| Tickets | `tickets.assign` | Assign a ticket: Assign or reassign to a person | Not built |
| Tickets | `tickets.transition` | Move a ticket between states: Manual transitions that are not automatic | Not built |
| Approvals | `approvals.history` | See approval history: Who decided what and when, on tickets you can see | Not built |
| Deployments and environments | `deployments.start` | Start a rollout: Start a plugin rollout once approved (sensitive) | Not built |
| Deployments and environments | `deployments.rollback` | Roll back: Roll back a rollout (sensitive) | Not built |
| Deployments and environments | `environments.view` | View environments: Env-1, Env-2 and provisioned environments | Not built |
| Deployments and environments | `environments.versions` | View deployed versions: Artifactory and Firewall plugin versions per environment | Not built |
| Deployments and environments | `environments.request` | Request an environment: Request a provisioned environment | Not built |
| Deployments and environments | `environments.extend` | Extend an environment's time to live: Extend within the maximum, with a ticket | Not built |
| Deployments and environments | `environments.manage` | Tear down an environment: Remove an environment now (sensitive) | Not built |
| Deployments and environments | `bundles.view` | View plugin bundles: Available bundle versions and checksums | Not built |
| Testing and evidence | `tests.run` | Run tests: Start a test run | Not built |
| Testing and evidence | `tests.view` | View test runs and results: Results per repository, format and mode | Not built |
| Testing and evidence | `evidence.view` | View evidence: Logs, responses and screenshots | Not built |
| Testing and evidence | `blockers.view` | View blockers: Issues that stop progress | Not built |
| Testing and evidence | `blockers.manage` | Raise and manage blockers: Classify, assign, resolve | Not built |
| Service management | `changes.manage` | Manage change records: Risk, rollback plan and window | Not built |
| Service management | `incidents.view` | View incidents: Open and recent incidents | Not built |
| Service management | `incidents.manage` | Manage incidents: Triage, update and resolve | Not built |
| Service management | `problems.manage` | Manage problems: Root cause and linked incidents | Not built |
| Service management | `sla.view` | View SLA clocks: Time left and breaches on tickets you can see | Not built |
| Service management | `kpi.view` | View the KPI dashboard: SLA and service KPIs | Not built |
| Service management | `cmdb.view` | View configuration items: Environments, repositories, versions and owners | Not built |
| Service management | `cmdb.edit` | Edit configuration items: Correct owner or notes | Not built |
| Reports | `reports.view` | View reports: Read published reports | Not built |
| Reports | `reports.generate` | Generate reports: Produce a report for a period | Not built |
| Status | `status.overview` | Lab status overview: Health of each component and environment | Not built |
| Status | `status.deployments` | Deployment status: Running and recent rollouts | Not built |
| Status | `operator.links` | Operator dashboard links: Links to the operator dashboards | Not built |
| Notifications | `notifications.view` | View notifications: In-portal notifications | Not built |
| Notifications | `notifications.preferences` | Set notification preferences: Choose what you receive by email and Teams | Not built |
| Administration | `users.read` | View users: See people, their roles and groups | Not built |
| Administration | `groups.read` | View groups: See groups, members and roles | Not built |

## What this role cannot do

- Approve your own change or any approval.
- Change roles, features or flags.
- Edit the audit log (nobody can).

## Notifications

You are told when: a workflow fails; an alarm opens an incident; an environment expires or the reaper fails; a change window starts; a ticket assigned to you changes.

## Related

[Roles](/Roles), [Monitoring and Alerting](/Operations/Monitoring-and-Alerting), [Automation](/Automation).
