# Services

Every service and component in the lab, who owns it, where its code is, and its status.

[[_TOC_]]

**Status legend.** **Built** = running and used. **Partly** = exists, with a stated gap. **Not built** = designed, nothing runs. A plan is never described as behaviour.

## Application services

| Service | Purpose | Repository | Runs on | Data | Status |
|---|---|---|---|---|---|
| Portal UI | Tickets, status, versions, uploads, approvals, evidence, reports, audit view | `mls-portal-ui-react` | EC2 (Auto Scaling) behind the internal ALB | none | Partly |
| Portal API | Ticket lifecycle, SLA, reports, CMDB-lite, audit | `mls-portal-api` | EC2 (Auto Scaling) | PostgreSQL (RDS) | Partly |
| IAM module | Users, groups, roles and scopes; scope lookup for other services; federation settings | `mls-portal-api` | Inside the Portal API | PostgreSQL | Not built |
| Keycloak | Single sign-on, MFA, federation to the Sonatype and Motivity identity providers (identity only, no roles) | Configuration in `mls-infra` | EC2, clustered | PostgreSQL (RDS) | Partly |
| Provisioning API | Request, inspect, extend and tear down environments | `mls-automation-api` | Private API Gateway + Lambda | Environment registry in the portal database | Not built |
| Provisioning CLI | The same operations from a terminal | `mls-automation-api` | Workstation or runner | none | Not built |
| Lifecycle engine | Starts and tracks ticket workflows | `mls-portal-api` | Inside the Portal API | PostgreSQL | Partly |
| Step Functions | Orchestrates the lifecycle, provisioning, reaping and upgrade checks | `mls-automation-api` | AWS | Execution history | Not built |
| Lambda functions | Workers called by the state machines | `mls-automation-api` | AWS Lambda, VPC-attached | S3 | Partly |
| TTL reaper | Removes expired environments | `mls-automation-api` | EventBridge Scheduler + Lambda | Environment registry | Not built |
| Test runner | Executes the Firewall test matrix | `mls-infra` (`verify/`) | Kubernetes Job | Evidence in S3 | Not built |
| Plugin bundle | The versioned Firewall plugin package | `mls-fwplugin-bundle` | S3 and the feed | none | Not built |
| Shared library | Security, permission and audit client | `mls-commons-lib` | Linked into services | none | Not built |

## Sonatype and repository platforms

| Service | Purpose | Runs on | Status |
|---|---|---|---|
| IQ Server (Firewall) | Policy evaluation for every repository and pipeline | EC2 x2 with shared storage and its own database | Not built |
| Artifactory Env-1 | Current-release testing | EKS, 3 pods | Not built |
| Artifactory Env-2 | Version matrix | EKS, 3 pods | Not built |
| Harbor | Container registry under test; uses its own bundled cache, no external Redis ([ADR-0012](/Decisions/ADR-0012-No-Kafka-Or-Redis-For-Now)) | EKS (Env-2), dedicated node group | Not built |
| JFrog Enterprise X / Artifactory SaaS | Hosted repository platform | SaaS | Not built |
| Cloudsmith | Hosted repository platform | SaaS | Not built |

## Delivery and platform services

| Service | Purpose | Runs on | Status |
|---|---|---|---|
| GitLab (open source) | Source and pipelines under test | EC2 | Not built |
| GitLab runners and Azure DevOps agents | Build agents | EC2, auto-scaled | Not built |
| AWS CodePipeline / CodeBuild | Pipelines under test | AWS | Not built |
| Argo CD | GitOps deployment | EKS | Not built |
| External Secrets | Delivers secrets to workloads | EKS | Not built |
| Elasticsearch, Logstash, Kibana | Log storage, processing and search | EC2 | Not built |
| CloudWatch and SNS | Metrics, alarms and alert routing | AWS | Not built |
| Image Builder pipeline | Hardened images | AWS | Not built |

Detail pages: [Step Functions](/Services/Step-Functions), [Lambda Functions](/Services/Lambda-Functions). The AWS resources behind these services are in the [Bill of Materials](/Architecture/Bill-of-Materials).

## Not used, by decision

There is no Kafka and no Redis service in the lab ([ADR-0012](/Decisions/ADR-0012-No-Kafka-Or-Redis-For-Now)). Products that ship their own cache (Harbor, Argo CD, GitLab) keep it. Our own services use in-process caches, PostgreSQL, API Gateway, SNS, Step Functions and EventBridge Scheduler.
