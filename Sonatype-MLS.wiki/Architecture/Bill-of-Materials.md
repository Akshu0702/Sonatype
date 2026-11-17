# Bill of Materials

Every AWS resource and software product in the primary region, taken from the Gate 1 deck and its addendum. Costs are in the commercial schedule, not here; the structure of the cost model is in [Total Cost of Ownership](/Architecture/Total-Cost-of-Ownership). Quantities are for the primary region unless stated.

[[_TOC_]]

**Status legend.** **Built** = running and used. **Partly** = exists, with a stated gap. **Not built** = designed, nothing runs. A plan is never described as behaviour.

## Compute and containers

| Item | Qty | Specification | Purpose | Source |
|---|---|---|---|---|
| Amazon EKS clusters | 2 | Managed control plane, private endpoint | Lab Env-1 and Env-2 | Gate 1 |
| EKS nodes for Artifactory | 6 | m6i.2xlarge (8 vCPU, 32 GiB), gp3 200 GB, 3 per cluster across 3 AZs | Artifactory Enterprise, 3 nodes per environment | Gate 1 |
| EKS system nodes | 4 | m6i.large, 2 per cluster | Add-ons, Fluent Bit, jobs | Gate 1 |
| Harbor node group | 1 | Dedicated group on Env-2, 2+ replicas | Highly available Harbor | Addendum (modified) |
| EC2: portal | 2 | t3.large, Auto Scaling group, 2 AZs | Portal UI and API | Gate 1 |
| EC2: Keycloak | 2 | t3.large, clustered | Identity provider | Gate 1 |
| EC2: IQ Server | 2 | m6i.xlarge with Amazon EFS | Firewall (HA) | Gate 1 |
| EC2: ELK | 5 | Elasticsearch 3 x r6i.large (500 GB gp3); Logstash m6i.large; Kibana t3.medium | Logging | Gate 1 |
| EC2: CI/CD | 3 | GitLab CE m6i.xlarge; 2 x c6i.xlarge runners and agents, auto-scaled | Pipelines under test | Gate 1 |
| EC2: bastion | 1 | Small instance, Systems Manager access | Administrative access | Gate 1 |
| Ephemeral capacity | Variable | Hardened Linux instances with PostgreSQL, quota-limited | Provisioned test environments | Addendum |
| AWS Lambda | 10 | Node.js, VPC-attached | Workers (see [Lambda Functions](/Services/Lambda-Functions)) | Gate 1 (6) plus addendum (4) |
| Step Functions | 5 state machines | Standard workflows with aliases | Orchestration | Addendum |
| API Gateway | 1 | Private API | Provisioning API | Addendum |

## Data

| Item | Qty | Specification | Purpose | Source |
|---|---|---|---|---|
| RDS PostgreSQL | 3 | db.m6g.large Multi-AZ, gp3 (200 GB Artifactory, 100 GB shared) | Artifactory x2; portal and Keycloak | Gate 1 |
| RDS PostgreSQL for IQ Server | 1 | Dedicated instance, Multi-AZ | Firewall data, isolated from the portal | Addendum (modified) |
| Amazon S3 | 5 buckets | SSE-KMS, versioning, replication | Artifacts, evidence, filestores, backups, logs | Gate 1 |
| S3 for Terraform state | 1 per account | Encrypted, versioned, locked | Infrastructure state | Addendum |
| Amazon EFS | 1 | Multi-AZ | IQ Server data | Gate 1 |
| Amazon ECR | 1 | Private registry with scanning | Container images | Gate 1 |

## Network

| Item | Qty | Specification | Source |
|---|---|---|---|
| Transit Gateway | 1 | 3 VPC attachments | Gate 1 |
| Site-to-Site VPN | 2 | Sonatype and Motivity, two tunnels each | Gate 1 |
| VPCs | 3 | Shared services 10.40.0.0/20, Env-1 10.41.0.0/20, Env-2 10.42.0.0/20 | Gate 1 |
| NAT gateways | 3 | One per availability zone | Gate 1 |
| AWS Network Firewall | 1 | Egress allow-list | Gate 1 |
| Load balancers | 1 ALB, 2 NLB | Internal only, ACM Private CA certificates | Gate 1 |
| Route 53 | 1 zone | Private hosted zone, inbound resolver endpoints, failover records | Gate 1 |
| VPC endpoints | Several | S3, ECR, STS, Secrets Manager, CloudWatch Logs, Lambda | Gate 1 |

## Security and identity

KMS (about 6 customer-managed keys), Secrets Manager, IAM Identity Center, GuardDuty (including EKS), Security Hub (CIS and AWS Foundational Security Best Practices), AWS Config, Inspector, CloudTrail with log-file validation, Systems Manager (Patch Manager and Session Manager). Source: Gate 1.

## Operations

CloudWatch (metrics, logs, alarms, Container Insights), SNS, AWS Backup, EventBridge Scheduler (reaper), Amazon SES (notifications, addendum), AWS Budgets (addendum).

## Disaster recovery (ap-south-2)

Pilot light: VPCs, Transit Gateway and VPN pre-provisioned; three RDS read replicas; S3 cross-region replication; backup vault; EFS replica; AMI copies; EKS and EC2 created through infrastructure as code when needed. Source: Gate 1.

## Not in the bill of materials, by decision

Kafka (including a managed Kafka service) and Redis (including ElastiCache) are not used ([ADR-0012](/Decisions/ADR-0012-No-Kafka-Or-Redis-For-Now)). Harbor, Argo CD and GitLab use the caches they ship with.

## Software and licences

Motivity procures these under SOW #8 unless stated.

| Software | Licence or form | Notes |
|---|---|---|
| JFrog Artifactory Enterprise | Self-hosted, 2 environments x 3 nodes | An Enterprise licence for every node |
| JFrog Enterprise X / Artifactory SaaS | SaaS tenant | One platform or two is to be confirmed |
| Cloudsmith | SaaS | |
| Harbor | Open source (Helm) | |
| GitLab | Open source (Community Edition) | |
| Azure DevOps | SaaS organisation, self-hosted agents | |
| AWS CodePipeline and CodeBuild | Pay per use | |
| Keycloak | Open source | |
| Elasticsearch, Logstash, Kibana | Elastic Basic | |
| Sonatype IQ Server and Firewall | Licence provided by Sonatype | HA licence scope to be confirmed |
| Terraform | Open source | Version pinned |
| Ansible | Open source | Version pinned |
| Argo CD | Open source | Proposed ([ADR-0007](/Decisions/ADR-0007-GitOps-With-Argo-CD)) |
| Helm | Open source | Version pinned |

## Related

[Services](/Services), [Terraform Modules](/Infrastructure/Terraform-Modules), [Architecture](/Architecture).
