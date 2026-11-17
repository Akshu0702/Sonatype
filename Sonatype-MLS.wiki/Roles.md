# Roles

What each person can do in the lab portal. A role is a set of features, and the portal administrator decides which features each role has.

[[_TOC_]]

**Status legend.** **Built** = running and used. **Partly** = exists, with a stated gap. **Not built** = designed, nothing runs. A plan is never described as behaviour.

The roles below are the **design**. Every feature is **Not built** until its story is closed, and the roles page for a person is updated in the same change as the feature.

## How roles work

1. **Signing in.** Single sign-on and MFA prove who you are.
2. **Users, groups and roles.** The portal's own database records who you are, which groups you belong to and which roles you hold, directly or through a group. None of this is taken from identity-provider claims.
3. **Scopes.** A role holds **scopes**. Each scope is one feature (for example `tickets.create_support`, "Create a support ticket"), and scopes are grouped into feature groups such as Tickets. The portal administrator assigns them to roles. See [Feature Flags](/Feature-Flags) and [Authentication and Authorization](/Architecture/Authentication-and-Authorization).
4. **Flags.** A feature must also be switched **on**. A feature that is off is unavailable to everyone, and is refused by the server, not just hidden on screen.
5. **Checks.** Every request is checked against your current scopes. A change by the administrator takes effect within seconds, because the lookup is cached for only 15 seconds.

## The roles

| Role | Who | What they mainly do | Page |
|---|---|---|---|
| Requester | Sonatype engineers | Raise tickets, upload builds, check status, follow up | [Requester](/Roles/Requester) |
| Approver | Sonatype reviewers and approvers | Decide approvals, review evidence, sign off reports | [Approver](/Roles/Approver) |
| Test engineer | Motivity | Run Firewall tests, capture evidence, manage blockers | [Test Engineer](/Roles/Test-Engineer) |
| Infra engineer | Motivity infrastructure and DevOps | Deploy and roll back, operate environments, handle incidents and changes | [Infra Engineer](/Roles/Infra-Engineer) |
| Portal administrator | Named administrators | Manage users, roles, features and flags, settings, audit | [Portal Administrator](/Roles/Portal-Administrator) |
| Automation service | The platform itself (not a person) | Scoped automated actions | [Automation Service](/Roles/Automation-Service) |

The default assignment of features to roles is in [Role Feature Assignment](/Feature-Flags/Role-Feature-Assignment). The administrator can change it.

## Who acts at each stage of a ticket

| Stage | What happens | Who acts | Features involved |
|---|---|---|---|
| S1 to S2 Intake | A ticket is raised, with the build file if there is one | Requester | `tickets.create_*`, `tickets.attach` |
| S3 Workspace | The build is checked and prepared | Automation | none (system) |
| Approval | A decision is made | Approver | `approvals.queue`, `approvals.decide` |
| S4 Deploy | The rollout starts | Automation, started by Infra engineer or the workflow | `deployments.start` |
| S5 Verify and restart | Pods restart; checksums checked | Automation | none (system) |
| S6 Version check | The deployed version is confirmed | Automation | none (system) |
| S7 Test | The Firewall test matrix runs | Test engineer (or the workflow) | `tests.run`, `evidence.view` |
| S8 Blockers | Issues are recorded, routed and resolved | Test engineer, Infra engineer | `blockers.manage` |
| S9 Report | Results are summarised and signed off | Test engineer or Infra engineer prepares; Approver signs | `reports.generate`, `reports.signoff` |
| Any time | Check status, follow up | Requester and others | `status.*`, `tickets.watch`, `tickets.chase`, `tickets.escalate` |

## Ticket states, in plain words

| State shown | Meaning |
|---|---|
| New | Raised, not yet picked up |
| Preparing | The build and workspace are being prepared |
| Awaiting approval | Waiting for an approver |
| Deploying | The rollout is running |
| Verifying | Checking the deployed version |
| Testing | Tests are running |
| Blocked | Something is stopping progress; the blocker says what and who owns it |
| Reporting | Results are being summarised |
| Closed | Done, with the report linked |

## Review of the role design

The six roles come from the Gate 1 access design and the kickoff scope ("SSO and 6 roles"). Reading them against the rest of the design raises these points. Each is recorded in [Known Issues and Gaps](/Operations/Known-Issues-and-Gaps) and needs a decision.

| Point | Why it matters | Proposal |
|---|---|---|
| The Gate 1 deck says roles are issued as claims in the sign-in token | Scopes must not come from identity-provider claims | Claims identify the person only; roles are assigned in the portal database |
| First-line operations engineers (triage, requests, provisioning) are not a named role | They need ticket handling and provisioning features, but not rollback or administration | Give them the Infra engineer role at first, or add a role; the administrator can restrict features either way |
| Sonatype management users who only view dashboards and reports are not a named role | They need read-only access | Allow a read-only role built from existing features, or a seventh built-in role |
| Roles are fixed at six | The administrator decides what each role can do | Adopt the Baton model: built-in roles cannot be deleted or renamed, the administrator can also create custom roles from the scope catalogue, and people are collected into groups that carry roles |
| A person may hold several roles | A person who is both Requester and Approver could approve their own request | Adopt Baton's persona rule ("Working as"): a person can narrow themselves to one role, and the own-ticket rule always applies |
| Features that decide approvals and deployments are sensitive | The person who approves must not be the person who deploys or administers | Guard rails in [Role Feature Assignment](/Feature-Flags/Role-Feature-Assignment) |

## Related

[Feature Flags](/Feature-Flags), [Portal and ITSM](/Portal-and-ITSM), [Portal API](/API-Reference/Portal-API), [Audit and Logging](/Architecture/Audit-and-Logging).
