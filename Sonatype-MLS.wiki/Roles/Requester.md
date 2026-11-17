# Requester

Raise work, give the lab what it needs, see how it is going, and follow it up.

[[_TOC_]]

**Status legend.** **Built** = running and used. **Partly** = exists, with a stated gap. **Not built** = designed, nothing runs. A plan is never described as behaviour.

**Who:** Sonatype engineers and others who ask the lab to do something.

**Sign-in:** single sign-on with MFA. What you see depends on the features the portal administrator has assigned to this role and switched on; if a feature below is missing, it has been switched off or not assigned.

## Create a ticket

1. Open **Tickets** and choose **New ticket**.
2. Choose the type: support, provisioning, plugin or environment change, incident, or access. You see only the types your role may create.
3. Give a short title in plain words, and a description: what you want, why, and by when.
4. For a plugin or environment change, attach the **build ZIP**. The portal records its checksum so everyone can see exactly which file was used.
5. Submit. You get a ticket number, and the ticket starts in **New**.

You can edit the description while the ticket is open, and cancel it before work starts.

## Get status

| You want to know | Where to look |
|---|---|
| Where my ticket is | The ticket page shows its state, the next step and who has it |
| Is a rollout running, and which version | **Status**, then **Deployments**; the ticket also shows the stage |
| What version each environment runs | **Environments** shows the Artifactory and Firewall plugin versions |
| Is the lab healthy | **Status**, then **Lab overview**: Healthy, Degraded, Down or Unknown |
| Did the tests pass, and why | The ticket's **Tests** tab, with links to the evidence |
| How long do I have to wait | The SLA clock on the ticket |

## Follow up

| You want to | Do this |
|---|---|
| Get told when anything changes | **Follow** the ticket; choose email, Teams or both in your preferences |
| Add information or answer a question | **Comment** on the ticket |
| Ask for an update | **Ask for an update**: a reminder goes to the assignee and is recorded on the ticket |
| Raise it because the SLA is at risk | **Escalate**: it goes along the escalation path and is recorded |
| Withdraw the request | **Cancel** before work starts |
| Reopen something closed | **Reopen** within the allowed window |

## Request or extend a test environment

1. Create a **provisioning ticket**, or open **Environments** and choose **Request**.
2. Choose a configuration (operating system image and database), a time to live and the ticket it belongs to.
3. The environment appears in **Environments** with its expiry. You are told before it expires.
4. To keep it longer, choose **Extend**, within the maximum, with a ticket.

## See reports and the service dashboard

Open **Reports** for the weekly and monthly reports, and **KPIs** for the SLA dashboard where your role has it.

## Features of this role

Default assignment. The portal administrator can change it ([Role Feature Assignment](/Feature-Flags/Role-Feature-Assignment)). Feature definitions are in the [Feature Catalogue](/Feature-Flags/Feature-Catalogue).

| Group | Scope (feature) | What it lets you do | Status |
|---|---|---|---|
| Tickets | `tickets.create_support` | Create a support ticket: Ask a question or report a problem with the lab | Not built |
| Tickets | `tickets.create_provisioning` | Create a provisioning ticket: Ask for a test environment | Not built |
| Tickets | `tickets.create_change` | Create a plugin or environment change ticket: Ask for a plugin deployment, upgrade or environment change, with the build file | Not built |
| Tickets | `tickets.create_incident` | Create an incident ticket: Report something that is broken | Not built |
| Tickets | `tickets.create_access` | Create an access ticket: Ask to join, change or leave (joiner, mover, leaver) | Not built |
| Tickets | `tickets.view_own` | View own tickets: See tickets you raised or follow | Not built |
| Tickets | `tickets.update` | Edit a ticket: Change description and fields while the ticket is open | Not built |
| Tickets | `tickets.comment` | Comment on a ticket: Add information or answer a question | Not built |
| Tickets | `tickets.attach` | Attach files: Upload a build ZIP or supporting file | Not built |
| Tickets | `tickets.watch` | Follow a ticket: Get notified of every change | Not built |
| Tickets | `tickets.chase` | Ask for an update: Send a reminder to the assignee without opening a new ticket | Not built |
| Tickets | `tickets.escalate` | Escalate a ticket: Raise it along the escalation path when the SLA is at risk | Not built |
| Tickets | `tickets.cancel` | Cancel a ticket: Withdraw your own ticket before work starts | Not built |
| Tickets | `tickets.reopen` | Reopen a ticket: Reopen a closed ticket within the allowed window | Not built |
| Approvals | `approvals.history` | See approval history: Who decided what and when, on tickets you can see | Not built |
| Deployments and environments | `environments.view` | View environments: Env-1, Env-2 and provisioned environments | Not built |
| Deployments and environments | `environments.versions` | View deployed versions: Artifactory and Firewall plugin versions per environment | Not built |
| Deployments and environments | `environments.request` | Request an environment: Request a provisioned environment | Not built |
| Deployments and environments | `environments.extend` | Extend an environment's time to live: Extend within the maximum, with a ticket | Not built |
| Testing and evidence | `tests.view` | View test runs and results: Results per repository, format and mode | Not built |
| Testing and evidence | `evidence.view` | View evidence: Logs, responses and screenshots | Not built |
| Testing and evidence | `blockers.view` | View blockers: Issues that stop progress | Not built |
| Service management | `sla.view` | View SLA clocks: Time left and breaches on tickets you can see | Not built |
| Reports | `reports.view` | View reports: Read published reports | Not built |
| Status | `status.overview` | Lab status overview: Health of each component and environment | Not built |
| Status | `status.deployments` | Deployment status: Running and recent rollouts | Not built |
| Notifications | `notifications.view` | View notifications: In-portal notifications | Not built |
| Notifications | `notifications.preferences` | Set notification preferences: Choose what you receive by email and Teams | Not built |

## What this role cannot do

- Approve or reject work.
- Start, roll back or tear down anything yourself.
- See other people's tickets, unless the administrator has given you that feature.
- Change roles or features.

## Notifications

You are told when: your ticket changes state; an approval is decided; a blocker is raised; test results are ready; an environment is about to expire; an SLA is at risk. You choose the channel in your preferences.

## Related

[Roles](/Roles), [Approver](/Roles/Approver), [Infra Engineer](/Roles/Infra-Engineer), [Feature Catalogue](/Feature-Flags/Feature-Catalogue).
