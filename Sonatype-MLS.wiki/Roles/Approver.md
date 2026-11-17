# Approver

Decide approvals, check the evidence, and sign off reports.

[[_TOC_]]

**Status legend.** **Built** = running and used. **Partly** = exists, with a stated gap. **Not built** = designed, nothing runs. A plan is never described as behaviour.

**Who:** Sonatype reviewers and approvers who decide whether work may go ahead.

**Sign-in:** single sign-on with MFA. What you see depends on the features the portal administrator has assigned to this role and switched on; if a feature below is missing, it has been switched off or not assigned.

## Decide an approval

1. Open **Approvals**. Tickets waiting for you are listed, oldest first, with the time left on the SLA.
2. Open a ticket. Read the request, the build file checksum, the change risk and rollback plan, and the history.
3. Choose **Approve** or **Reject** and give a reason. The reason is required.
4. The decision, your name and the time are recorded. An approval starts the rollout; a rejection returns the ticket to the requester.

You cannot decide your own ticket, even if you also hold the Requester role.

## Review evidence

Open the ticket's **Tests** tab: results per repository, format and mode, with links to logs, responses and screenshots. A failed case shows the expected and the actual result.

## Sign off a report

1. Open **Reports** and choose a report in review.
2. Read the results, blockers and any accepted risks.
3. **Sign off**. The report is then frozen; a correction later is a dated, signed addendum.

## Watch progress and service levels

- **Status** shows lab health and running rollouts.
- **KPIs** shows SLA performance and trends.
- **Escalate** a ticket if it is at risk.

## Features of this role

Default assignment. The portal administrator can change it ([Role Feature Assignment](/Feature-Flags/Role-Feature-Assignment)). Feature definitions are in the [Feature Catalogue](/Feature-Flags/Feature-Catalogue).

| Group | Scope (feature) | What it lets you do | Status |
|---|---|---|---|
| Tickets | `tickets.create_support` | Create a support ticket: Ask a question or report a problem with the lab | Not built |
| Tickets | `tickets.view_own` | View own tickets: See tickets you raised or follow | Not built |
| Tickets | `tickets.view_all` | View all tickets: See every ticket in your organisation scope | Not built |
| Tickets | `tickets.comment` | Comment on a ticket: Add information or answer a question | Not built |
| Tickets | `tickets.watch` | Follow a ticket: Get notified of every change | Not built |
| Tickets | `tickets.escalate` | Escalate a ticket: Raise it along the escalation path when the SLA is at risk | Not built |
| Approvals | `approvals.queue` | See the approval queue: Tickets waiting for a decision | Not built |
| Approvals | `approvals.decide` | Approve or reject: Decide a deploy or restart, with a reason (sensitive) | Not built |
| Approvals | `approvals.history` | See approval history: Who decided what and when, on tickets you can see | Not built |
| Deployments and environments | `environments.view` | View environments: Env-1, Env-2 and provisioned environments | Not built |
| Deployments and environments | `environments.versions` | View deployed versions: Artifactory and Firewall plugin versions per environment | Not built |
| Deployments and environments | `bundles.view` | View plugin bundles: Available bundle versions and checksums | Not built |
| Testing and evidence | `tests.view` | View test runs and results: Results per repository, format and mode | Not built |
| Testing and evidence | `evidence.view` | View evidence: Logs, responses and screenshots | Not built |
| Testing and evidence | `blockers.view` | View blockers: Issues that stop progress | Not built |
| Service management | `incidents.view` | View incidents: Open and recent incidents | Not built |
| Service management | `sla.view` | View SLA clocks: Time left and breaches on tickets you can see | Not built |
| Service management | `kpi.view` | View the KPI dashboard: SLA and service KPIs | Not built |
| Service management | `cmdb.view` | View configuration items: Environments, repositories, versions and owners | Not built |
| Reports | `reports.view` | View reports: Read published reports | Not built |
| Reports | `reports.signoff` | Sign off a report: Accept a report; it is then frozen (sensitive) | Not built |
| Status | `status.overview` | Lab status overview: Health of each component and environment | Not built |
| Status | `status.deployments` | Deployment status: Running and recent rollouts | Not built |
| Notifications | `notifications.view` | View notifications: In-portal notifications | Not built |
| Notifications | `notifications.preferences` | Set notification preferences: Choose what you receive by email and Teams | Not built |
| Administration | `users.read` | View users: See people, their roles and groups | Not built |

## What this role cannot do

- Decide your own ticket.
- Start or roll back a rollout.
- Change roles, features or settings.
- Edit a signed report.

## Notifications

You are told when: an approval is waiting (and reminded if it waits too long); a report is ready to sign; a ticket you follow changes; a rollback happened.

## Related

[Roles](/Roles), [Requester](/Roles/Requester), [Role Feature Assignment](/Feature-Flags/Role-Feature-Assignment).
