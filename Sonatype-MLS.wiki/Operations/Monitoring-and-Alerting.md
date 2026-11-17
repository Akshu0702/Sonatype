# Monitoring and Alerting

How the lab is watched, how a problem reaches a person, and how infrastructure and deployment status are shown.

[[_TOC_]]

**Status legend.** **Built** = running and used. **Partly** = exists, with a stated gap. **Not built** = designed, nothing runs. A plan is never described as behaviour.

**State: Not built.** This is the design from the Gate 1 observability slide, extended for deployment status. Thresholds are **starting points**, tuned after the first weeks of real use.

## What is monitored

| Area | Question it answers | Main sources |
|---|---|---|
| Infrastructure status | Is each component up and healthy? | CloudWatch metrics and Container Insights, health checks, VPN and load balancer metrics |
| Deployment status | What is being deployed, what version runs, did it work? | Step Functions executions, Argo CD application state, `firewallVersion` checks, pipeline runs |
| Environment status | Which provisioned environments exist, who owns them, when do they expire? | Environment registry, reaper results |
| Service health | Do Artifactory, IQ Server, Harbor, the portal and Keycloak respond correctly? | Health endpoints, synthetic checks |
| Firewall effectiveness | Is the Firewall still blocking what it should? | Scheduled canary checks against each repository |
| Backup and DR | Did backups run, is replication current? | AWS Backup, replication metrics |
| Security | Are there findings, unusual sign-ins or changes? | GuardDuty, Security Hub, Config, CloudTrail, audit events |
| Capacity and cost | Are quotas, disks and budgets under control? | Service Quotas, CloudWatch, AWS Budgets |
| Service management | Are SLAs and blockers on track? | Portal SLA clocks |

## How signals flow

::: mermaid
flowchart LR
  subgraph SRC["Sources"]
    INF["Infrastructure: EC2, EKS, RDS, EFS, VPN, ALB"]
    APP["Services: Artifactory, IQ, Harbor, portal, Keycloak"]
    WF["Workflows: Step Functions, Argo CD, pipelines"]
    SEC["Security: GuardDuty, Security Hub, Config, CloudTrail"]
    SYN["Synthetic checks"]
  end
  INF --> CW["CloudWatch metrics and alarms"]
  APP --> CW
  APP --> ELK["Logs: Elasticsearch, Kibana"]
  WF --> CW
  WF --> ELK
  SEC --> CW
  SYN --> CW
  CW --> SNS["SNS topics by severity"]
  ELK --> SNS
  SNS --> RT["Router function"]
  RT --> INC["Portal incident"]
  RT --> PG["Paging for P1 and P2"]
  RT --> TM["Teams and email"]
  APP --> ST["Status function: fn-env-status"]
  WF --> ST
  ST --> VIEW["Portal status views"]
:::

## Severity and routing

| Severity | Meaning | Route |
|---|---|---|
| P1 | The lab is unavailable, data is at risk, or there is a serious security event | Portal incident and page |
| P2 | A component is degraded or a workflow has failed | Portal incident and page during support hours |
| P3 | Needs attention in business hours | Portal incident and Teams notice |
| P4 | Information | Email digest |

Response and resolution targets come from the SLA definitions, which are not yet agreed (see [Known Issues and Gaps](/Operations/Known-Issues-and-Gaps)). Delivery hours are 11 AM to 7 PM IST on weekdays, so the paging arrangement for P1 and P2 outside those hours must be decided.

## How an alert becomes an incident

1. An alarm or log rule fires and publishes to an SNS topic for its severity.
2. A router function calls the portal's internal alarm endpoint ([Portal API](/API-Reference/Portal-API)).
3. The portal opens an incident, links the affected environment or component, and starts the SLA clock.
4. P1 and P2 also page the on-call person; P3 posts to Teams.
5. When the alarm clears, the portal marks the incident as recovered but leaves closure to a person.
6. Repeated alarms for the same cause update one incident instead of opening new ones.

## On-call and escalation

| Item | State |
|---|---|
| On-call rota for the Motivity operations team | Not built |
| Paging tool | Not chosen |
| Escalation path (Motivity then Sonatype contacts) | Defined in the kickoff; to be wired into the portal |
| Runbook for every P1 and P2 alert | Written with the workflow it covers |

## Rules

- **Every alert has an owner and a runbook.** An alert with neither is not added.
- **Every alert has a reason to wake someone.** A condition that never needs action is a dashboard panel, not an alert.
- **A failed deployment must be visible where a person looks.** A failed workflow opens a blocker on the ticket and an incident; it does not rely on a green build.
- **Alarms are code.** They are defined in the `cloudwatch-alarms` Terraform module and reviewed by pull request ([Terraform Modules](/Infrastructure/Terraform-Modules)).
- **Review monthly.** Noisy alerts are tuned or removed; missed incidents add alerts.
- **No secrets in alerts or dashboards.** Logs are masked before they are indexed.

## Related

[Alert Catalogue](/Operations/Alert-Catalogue), [Dashboards and Status](/Operations/Dashboards-and-Status), [Audit and Logging](/Architecture/Audit-and-Logging), [Well-Architected Review](/Architecture/Well-Architected-Review).
