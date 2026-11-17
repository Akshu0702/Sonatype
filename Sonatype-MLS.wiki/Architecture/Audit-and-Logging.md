# Audit and Logging

What is recorded, where it goes, who can read it, and how long it is kept.

[[_TOC_]]

**Status legend.** **Built** = running and used. **Partly** = exists, with a stated gap. **Not built** = designed, nothing runs. A plan is never described as behaviour.

**State: Not built** for the target design. The prototype portal has some audit capability that has not yet been reviewed against this page.

## Two kinds of record

| Kind | Holds | Used for |
|---|---|---|
| Entity change | Which record changed, the old and new values, who did it | Showing what changed in a ticket, approval or environment |
| Security event | Sign-in, sign-out, MFA, role and permission changes, failed access | Security review and incident investigation |

They are different shapes, so they are different event types in one table, `audit_event`, with a type column.

## The audit event

| Column | Meaning |
|---|---|
| `id` | Unique id |
| `occurred_at` | UTC time of the action |
| `kind` | `entity_change` or `security_event` |
| `actor_id`, `actor_org` | Who did it and from which organisation |
| `acting_for_org` | The organisation whose data was acted on. It differs from `actor_org` only when one party acts on the other's behalf, and both are always stored |
| `action` | For example `ticket.create`, `approval.decide`, `role.grant` |
| `entity_type`, `entity_id` | What was acted on |
| `ticket_id` | The ticket, when there is one |
| `outcome` | `success` or `failure`, with a reason code |
| `source_ip`, `user_agent`, `request_id` | Where from, and a trace id shared with the logs |
| `execution_arn` | The workflow execution, for automated actions |
| `details` | JSON: the old and new values for a change |
| `prev_hash`, `hash` | Tamper evidence (see below) |

## What is audited

| Area | Actions |
|---|---|
| Identity | Sign-in, sign-out, MFA result, failed sign-in, role or permission granted or removed, user created or disabled |
| Tickets | Create, update, transition, comment, attachment added |
| Approvals | Approve, reject, with reason |
| Deployments | Bundle selected, rollout started, version verified, rollback |
| Environments | Request, create, extend TTL, tear down, expire |
| Tests | Run started, result recorded, evidence stored |
| Service management | Change, incident, problem and blocker changes; SLA breach |
| Reports | Generated, signed off |
| Administration | Settings changed, access recertified, break-glass use, feature flag changed, scope added to or removed from a role, role or group membership changed (with who confirmed it) |

## How events are written

- Every service writes audit events through one shared client in `mls-commons-lib`. No service writes the table by another route.
- There is no message broker ([ADR-0012](/Decisions/ADR-0012-No-Kafka-Or-Redis-For-Now)). Automation delivers events through the portal's internal endpoint, retried by the workflow. A queue is an option if events are lost; it is not adopted.
- Row-level security and the application layer both enforce the organisation boundary.
- Automated actions record the workflow execution so a person can trace from an action to the ticket and the approval.

## Tamper evidence (proposed)

Each event stores the hash of the previous event plus its own content, giving a chain that can be verified end to end. A verify endpoint reports the first broken sequence. The chain head is exported to S3 with Object Lock on a schedule so it exists outside the database. This is proposed, not yet decided.

## Retention and access

| Store | Retention | Notes |
|---|---|---|
| Portal database | Proposed: 12 months online, partitioned by month; older partitions dropped after export | Automatic, never manual |
| Elasticsearch | 30 days hot | From the Gate 1 design |
| S3 archive | One year of snapshots | From the Gate 1 design; Object Lock for exported audit data (proposed) |

Read access is the `audit.read` permission, held by the portal administrator and by named reviewers. Nobody can edit or delete an audit event; there is no such permission.

## Log sources

| Source | Goes to |
|---|---|
| Portal and services (application logs) | Filebeat to Logstash to Elasticsearch |
| EKS pods | Fluent Bit to Logstash |
| Lambda | CloudWatch Logs subscription to Logstash |
| Load balancers, VPC flow logs | S3 to Logstash |
| CloudTrail, Config, GuardDuty, Security Hub | Their native stores, forwarded to the log platform |
| Step Functions execution history | CloudWatch Logs |
| Keycloak events | Keycloak event listener to the audit table and the log platform |

Logstash masks credentials and tokens before indexing. Kibana access uses single sign-on.

## Alerts

CloudWatch alarms route through SNS to the portal, which opens an incident. Audit-specific alerts: repeated failed sign-ins, role changes outside change windows, break-glass use, a broken audit chain.

## Related

[Data Model](/Architecture/Data-Model), [Security and Compliance](/Security-and-Compliance), [Well-Architected Review](/Architecture/Well-Architected-Review).
