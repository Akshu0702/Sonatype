# Test Engineer

Run tests, capture evidence, manage blockers and prepare reports.

[[_TOC_]]

**Status legend.** **Built** = running and used. **Partly** = exists, with a stated gap. **Not built** = designed, nothing runs. A plan is never described as behaviour.

**Who:** Motivity engineers who run the Firewall tests and keep the evidence.

**Sign-in:** single sign-on with MFA. What you see depends on the features the portal administrator has assigned to this role and switched on; if a feature below is missing, it has been switched off or not assigned.

## Run a test

1. Open the ticket, then **Tests**, then **Run**.
2. Choose the environment and the part of the matrix to run (repository, format, mode), or the full matrix.
3. Watch the run. Each case shows expected and actual results, and its evidence is stored automatically.
4. A failed case can become a **blocker** from the result.

## Manage blockers

| You want to | Do this |
|---|---|
| Record an issue that stops progress | **Raise a blocker** on the ticket, choose its class and severity |
| Route it | Assign an owner; the owner is notified |
| Resolve it | Add the resolution and **Resolve** |
| Check the fix | **Retest** the affected cases |

## Prepare a report

Choose **Reports**, then **Generate** for a period or a ticket. The approver signs it off.

## Check status

**Status** shows lab health and deployments; **Environments** shows versions; **Operator links** opens the dashboards.

## Features of this role

Default assignment. The portal administrator can change it ([Role Feature Assignment](/Feature-Flags/Role-Feature-Assignment)). Feature definitions are in the [Feature Catalogue](/Feature-Flags/Feature-Catalogue).

| Group | Scope (feature) | What it lets you do | Status |
|---|---|---|---|
| Tickets | `tickets.create_support` | Create a support ticket: Ask a question or report a problem with the lab | Not built |
| Tickets | `tickets.create_provisioning` | Create a provisioning ticket: Ask for a test environment | Not built |
| Tickets | `tickets.create_incident` | Create an incident ticket: Report something that is broken | Not built |
| Tickets | `tickets.create_problem` | Create a problem ticket: Record the underlying cause of repeated incidents | Not built |
| Tickets | `tickets.view_own` | View own tickets: See tickets you raised or follow | Not built |
| Tickets | `tickets.view_all` | View all tickets: See every ticket in your organisation scope | Not built |
| Tickets | `tickets.comment` | Comment on a ticket: Add information or answer a question | Not built |
| Tickets | `tickets.watch` | Follow a ticket: Get notified of every change | Not built |
| Tickets | `tickets.transition` | Move a ticket between states: Manual transitions that are not automatic | Not built |
| Approvals | `approvals.history` | See approval history: Who decided what and when, on tickets you can see | Not built |
| Deployments and environments | `environments.view` | View environments: Env-1, Env-2 and provisioned environments | Not built |
| Deployments and environments | `environments.versions` | View deployed versions: Artifactory and Firewall plugin versions per environment | Not built |
| Deployments and environments | `environments.request` | Request an environment: Request a provisioned environment | Not built |
| Deployments and environments | `bundles.view` | View plugin bundles: Available bundle versions and checksums | Not built |
| Testing and evidence | `tests.run` | Run tests: Start a test run | Not built |
| Testing and evidence | `tests.retest` | Retest: Run again after a blocker is resolved | Not built |
| Testing and evidence | `tests.view` | View test runs and results: Results per repository, format and mode | Not built |
| Testing and evidence | `evidence.view` | View evidence: Logs, responses and screenshots | Not built |
| Testing and evidence | `blockers.view` | View blockers: Issues that stop progress | Not built |
| Testing and evidence | `blockers.manage` | Raise and manage blockers: Classify, assign, resolve | Not built |
| Service management | `incidents.view` | View incidents: Open and recent incidents | Not built |
| Service management | `sla.view` | View SLA clocks: Time left and breaches on tickets you can see | Not built |
| Service management | `cmdb.view` | View configuration items: Environments, repositories, versions and owners | Not built |
| Reports | `reports.view` | View reports: Read published reports | Not built |
| Reports | `reports.generate` | Generate reports: Produce a report for a period | Not built |
| Status | `status.overview` | Lab status overview: Health of each component and environment | Not built |
| Status | `status.deployments` | Deployment status: Running and recent rollouts | Not built |
| Status | `operator.links` | Operator dashboard links: Links to the operator dashboards | Not built |
| Notifications | `notifications.view` | View notifications: In-portal notifications | Not built |
| Notifications | `notifications.preferences` | Set notification preferences: Choose what you receive by email and Teams | Not built |

## What this role cannot do

- Approve work.
- Start or roll back a rollout, or tear down an environment, unless the administrator has given you those features.
- Change roles or features.

## Notifications

You are told when: a run finishes; a blocker is assigned to you; a retest is due; a ticket you follow changes.

## Related

[Roles](/Roles), [Infra Engineer](/Roles/Infra-Engineer), [Dashboards and Status](/Operations/Dashboards-and-Status).
