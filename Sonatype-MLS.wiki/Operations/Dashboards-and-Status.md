# Dashboards and Status

What people can see about the lab's infrastructure and deployments, and where.

[[_TOC_]]

**Status legend.** **Built** = running and used. **Partly** = exists, with a stated gap. **Not built** = designed, nothing runs. A plan is never described as behaviour.

**State: Not built.** The portal status views are new; the operator dashboards use the tools in the Gate 1 design.

## Status model

Each component and environment shows one of four states.

| State | Meaning |
|---|---|
| Healthy | Health checks pass and no alarm is active |
| Degraded | A component is up but an alarm is active, or fewer than the expected instances are ready |
| Down | A health check is failing, or the component is unreachable |
| Unknown | Status could not be read. This is never shown as healthy |

A parent takes the worst state of its children. For example Env-1 is Degraded if one of three Artifactory pods is not ready.

## Portal status views (for Sonatype and Motivity users)

| View | Shows | Who |
|---|---|---|
| Lab overview | A tile per component (portal, Keycloak, IQ Server, Env-1, Env-2, Harbor, SaaS repositories, CI/CD), with status and the time of the last check | Every role |
| Environments | For Env-1 and Env-2: Artifactory version, Firewall plugin version, pods ready, last deployment, active alarms | Every role |
| Deployments | Running and recent rollouts: ticket, stage (S1 to S9), approval waiting, version being deployed, result, rollback | Every role |
| Provisioned environments | Active environments, configuration, owner, ticket, time to expiry | Requesters see their own; engineers see all |
| Test runs | Latest Firewall matrix results by repository, format and mode, with links to evidence | Test engineer, approver |
| Incidents and blockers | Open items by severity and age | Every role |
| SLA and KPI | Clocks, breaches, trends, weekly and monthly report | Approver, administrator |

Status is read through the status function (`fn-env-status`) and the deployed-versions function, cached for a short time. It is not stored as history; history comes from CloudWatch and the audit events.

## Operator dashboards

| Dashboard | Tool | Shows |
|---|---|---|
| Infrastructure | CloudWatch | VPN tunnels, Transit Gateway, load balancers, EC2, EKS nodes and pods, RDS, EFS |
| Artifactory and IQ Server | CloudWatch and Kibana | Health, request rate, errors, JVM, storage |
| Workflows | Step Functions console and CloudWatch | Executions by state machine, failures, durations, waiting approvals |
| GitOps | Argo CD | Application sync and health per cluster |
| Pipelines | Azure DevOps, GitLab and CodePipeline views | Recent runs and failures |
| Security | Security Hub and GuardDuty | Findings and standards scores |
| Backup and DR | CloudWatch and AWS Backup | Job results, replica lag, last restore test |
| Cost | AWS Budgets and Cost Explorer | Spend against budget, by tag |
| Logs | Kibana | Saved searches per service, audit view |

Dashboards are defined as code where the tool allows it, so a rebuilt environment gets them back.

## Health checks

| Component | Check |
|---|---|
| Artifactory | The system ping endpoint on each node, and a download of a known-good component |
| IQ Server | The server health endpoint on each node |
| Portal and Keycloak | Their health endpoints and a scheduled sign-in test |
| Harbor | Its health endpoint |
| SaaS repositories | A scheduled request through the egress path |
| Firewall | A canary: request a known-bad component and confirm it is quarantined, and a known-good one and confirm it is allowed |

## Deployment status data

| Data | Source |
|---|---|
| Which workflow is running and at which stage | Step Functions execution and the ticket |
| Whether a rollout is in progress | Argo CD application state |
| Which version each pod runs | The version check function (`firewallVersion`, Artifactory version) |
| Who approved and when | Portal approval records |
| What changed | Git history of `mls-infra` and the audit event |

## Related

[Monitoring and Alerting](/Operations/Monitoring-and-Alerting), [Alert Catalogue](/Operations/Alert-Catalogue), [Portal API](/API-Reference/Portal-API), [Services](/Services).
