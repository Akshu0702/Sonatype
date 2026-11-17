# Managed Lab Overview

Purpose, scope and the current state of every capability.

[[_TOC_]]

## Scope (SOW #8)

One-time build in six weeks (26-Oct to 04-Dec-2026), then managed services to 25-Oct-2027. Go-live is Sonatype Acceptance Testing sign-off. Delivery hours are 11 AM to 7 PM IST, Monday to Friday.

| Area | In scope |
|---|---|
| Hosting and security | Dedicated AWS in India (Mumbai primary, Hyderabad DR). VPN-only access, MFA, RBAC, secrets, monitoring, backup and DR |
| Sonatype Firewall | IQ Server in HA, integrated with every repository and CI/CD platform below |
| Repositories | Artifactory SaaS, self-hosted Artifactory, JFrog Enterprise X, Cloudsmith, self-hosted Harbor; Maven, npm, PyPI, NuGet, Docker/OCI |
| CI/CD | AWS CodePipeline/CodeBuild, GitLab, Azure DevOps, with policy gates that stop a pipeline on breach |
| Provisioning | On-demand hardened Linux + PostgreSQL environments through an API/CLI, with TTL and auto-teardown |
| Case management | Ticket portal with SSO, six roles, ticket lifecycle, approval gates, test evidence, blockers, reports and SLA/KPI dashboard |
| Operations readiness | Runbooks, SOPs, monitoring, SLA/KPI definitions, on-call, knowledge transfer |

## Success criteria

- Firewall integrated with all five repositories and three CI/CD platforms; block, quarantine, alert and pipeline-stop validated per package format, with evidence.
- An environment is provisioned by API/CLI and torn down automatically for each priority configuration.
- Sonatype users sign in through SSO, raise tickets and see the SLA/KPI dashboard and weekly report.
- HA failover, backup restore and DR tested; runbooks and knowledge transfer complete.
- Acceptance sign-off by Fri 04-Dec-2026.

## Capability map and status

**Status legend.** **Built** = running and used. **Partly** = exists, with a stated gap. **Not built** = designed, nothing runs. A plan is never described as behaviour.

| Capability | Status | Notes |
|---|---|---|
| AWS foundation (accounts, network, VPN, KMS, secrets) | Not built | Gate 1 design in review |
| Identity (Keycloak SSO, MFA, six roles) | Partly | Prototype portal exists; not yet on AWS |
| IQ Server Firewall in HA | Not built | HA licence scope to be confirmed |
| Artifactory Env-1 and Env-2 on EKS | Not built | Current manual runbook is the starting point |
| Harbor | Not built | Env-2 cluster |
| Artifactory SaaS, Enterprise X, Cloudsmith | Not built | Licences are the critical path |
| Plugin deployment automation | Partly | Manual runbook exists; automated bundle flow designed |
| Ticket lifecycle S1-S9 on Step Functions | Not built | Designed in [Automation](/Automation) |
| On-demand provisioning with TTL | Not built | Designed in [Automation](/Automation) |
| Firewall test harness | Not built | Matrix of repository x format x mode |
| Portal ticketing | Partly | Prototype |
| ITSM features (change, incident, problem, SLA, CMDB-lite) | Not built | See [Portal and ITSM](/Portal-and-ITSM) |
| Observability and alerting | Not built | ELK and CloudWatch. See [Monitoring and Alerting](/Operations/Monitoring-and-Alerting) |
| Infrastructure and deployment status views | Not built | See [Dashboards and Status](/Operations/Dashboards-and-Status) |
| Security baselines (CIS, AWS FSBP, OWASP) | Not built | See [Security and Compliance](/Security-and-Compliance) |
| Well-Architected review | Not built | See [Well-Architected Review](/Architecture/Well-Architected-Review) |
| Cost tracking and monthly cost review | Not built | See [Total Cost of Ownership](/Architecture/Total-Cost-of-Ownership) |
| Infrastructure as code (Terraform, Ansible) | Not built | See [Terraform Modules](/Infrastructure/Terraform-Modules) |
| GitOps deployment (Helm, Argo CD) | Not built | See [Helm and Argo CD](/Infrastructure/Helm-and-Argo-CD) |
| Portal API specification (OpenAPI) | Not built | See [API Reference](/API-Reference) |
| Portal data model and audit design | Not built | See [Data Model](/Architecture/Data-Model) |
| Backup and DR | Not built | Pilot light in ap-south-2 |

## Users and roles

| Role | Does |
|---|---|
| Sonatype requester | Raises tickets, uploads artifacts, views status and versions |
| Sonatype reviewer or approver | Approves deploy and restart, reviews and signs reports |
| Motivity test engineer | Runs tests, captures evidence, manages blockers |
| Motivity infra/DevOps | Deploys through approved runbooks, operates clusters |
| Portal administrator | Manages users, roles, settings; cannot approve own changes |
| Automation (service) | Scoped actions on one bucket or cluster |

Each role has a guide in [Roles](/Roles). What each role can do is decided by the portal administrator through [Feature Flags](/Feature-Flags).

## Glossary

- **Firewall**: Sonatype Repository Firewall, powered by IQ Server.
- **Modes**: audit, quarantine, NCP (namespace confusion protection), PCCS (policy-compliant component selection), disabled.
- **S1-S9**: the ticket lifecycle stages, from intake to report.
- **Plugin bundle**: the versioned, checksummed set of Firewall plugin files.
- **TTL**: time to live of a provisioned environment.
