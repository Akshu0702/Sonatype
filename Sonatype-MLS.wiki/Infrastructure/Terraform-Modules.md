# Terraform Modules

How AWS infrastructure is defined, versioned, planned and applied.

[[_TOC_]]

**Status legend.** **Built** = running and used. **Partly** = exists, with a stated gap. **Not built** = designed, nothing runs. A plan is never described as behaviour.

## Where the code lives

`mls-infra`, folder `terraform/`. See [Repository Layouts](/Engineering/Repository-Layouts).

| Folder | Holds |
|---|---|
| `terraform/modules/<module>` | Reusable modules. One folder per module, with its own README, inputs and outputs |
| `terraform/live/shared` | Shared services account: network hub, identity, portal, Keycloak, IQ Server, ELK, CI/CD hosts |
| `terraform/live/lab` | Lab account: Env-1 and Env-2 VPCs, EKS clusters, RDS, S3 filestores |
| `terraform/live/dev` | Build and test account for the portal and automation |
| `terraform/live/dr` | Hyderabad (ap-south-2) pilot light |

An environment is a folder of module calls and variable files. It holds no resource definitions of its own, so two environments differ only in their variables.

## Module catalogue

| Module | Purpose | Key resources | Status |
|---|---|---|---|
| `naming` | Standard names and tags from the naming standard; rejects a name or owner that breaks it | Pure logic: name, tags, tags with Name | Not built (draft pull request, 9 tests pass) |
| `tf-state-backend` | Encrypted state bucket and lock table | S3 bucket (versioned, private, TLS only), DynamoDB lock table | Not built (draft pull request, 5 tests pass) |
| `account-baseline` | Guardrails for every account | Service control policies, CloudTrail with log validation, AWS Config, GuardDuty, Security Hub, S3 Block Public Access | Not built |
| `network-vpc` | A VPC with subnets per AZ | VPC, subnets (app, data, TGW, public), route tables, NAT, S3 gateway endpoint, flow logs. Interface endpoints (ECR, STS, Secrets Manager, CloudWatch Logs) are a separate module | Not built (draft pull request, 7 tests pass) |
| `transit-gateway` | Hub and segmented routing | Transit Gateway, attachments, route tables | Not built (draft pull request, 10 tests pass) |
| `vpn-site-to-site` | Sonatype and Motivity access | Customer gateways, VPN connections (two tunnels, BGP) | Not built (draft pull request, 6 tests pass) |
| `dns` | Private naming | Route 53 private zone, resolver inbound endpoints, failover records | Not built (draft pull request, 6 tests pass) |
| `egress-control` | Controlled outbound traffic | NAT gateway per AZ, Network Firewall, allow-list rules | Not built |
| `identity-center` | Administrator access | Permission sets, assignments, break-glass role | Not built |
| `kms-keys` | Encryption keys | Customer-managed keys and aliases per data class | Not built (draft pull request, 5 tests pass) |
| `secrets` | Secret storage | Secrets Manager secrets, rotation, resource policies | Not built (draft pull request, 7 tests pass) |
| `eks-cluster` | Lab cluster | EKS control plane (private endpoint), node groups, OIDC provider, IAM roles for service accounts | Not built (draft pull request 750, 12 tests pass; node hardening in draft pull request 775, 20 tests pass) |
| `eks-addons` | IAM roles for the add-ons' service accounts, and the EBS CSI driver add-on | Roles for EBS CSI, Load Balancer Controller, Cluster Autoscaler and External Secrets (each assumable only by its own service account); the EBS CSI managed add-on. The apps themselves are Helm charts | Not built (draft pull request, 13 tests pass) |
| `rds-postgres` | Managed database | Multi-AZ instance, parameter group, subnet group, backups | Not built (draft pull request, 9 tests pass) |
| `s3-bucket` | Object storage | Bucket with SSE-KMS, versioning, lifecycle, optional replication | Not built (draft pull request, 10 tests pass) |
| `efs-filesystem` | Shared file storage | Multi-AZ EFS, mount targets, replication | Not built |
| `ec2-service` | Host-based services | Launch template, Auto Scaling group, security group, instance profile | Not built |
| `load-balancer` | Internal ingress | ALB and NLB, listeners, target groups, ACM Private CA certificates | Not built |
| `ecr-registry` | Container images | Private registry with scan on push | Not built |
| `observability-elk` | Log platform | Elasticsearch nodes, Logstash, Kibana, snapshot bucket | Not built |
| `cloudwatch-alarms` | Alerting | Alarms, SNS topics, routing to portal incidents | Not built |
| `backup` | Backup policy | AWS Backup plans, vaults, cross-region copy | Not built |
| `image-builder` | Hardened images | Image Builder pipeline, recipes, scans, distribution | Not built |
| `provisioning-env` | On-demand environment | Hardened Linux instance, PostgreSQL, mandatory TTL, owner and ticket tags | Not built |
| `ttl-reaper` | Environment cleanup | EventBridge Scheduler, reaper function, teardown evidence | Not built |
| `step-functions` | Orchestration | State machine, alias, IAM role, logging | Not built |
| `lambda-function` | Function packaging | Function, role scoped to one bucket or cluster, log group | Not built |
| `api-gateway-private` | Provisioning API front door | Private API, VPC endpoint, authoriser | Not built |
| `notifications` | Email and Teams | SES identities, webhook secret | Not built |
| `dr-region` | Pilot-light DR | Replicas, replicated buckets, backup vault, scaled-to-zero compute | Not built |
| `bastion` | Admin host access | Bastion instance, Systems Manager access | Not built |

## Testing

Every module has tests in its own `tests/` folder, run with `terraform test`. They use a mock AWS provider, so they run offline with no account and no credentials. CI runs `terraform fmt -check`, `validate` and `test` for every module, from a pinned container image (`hashicorp/terraform:1.9.8`). A module is **not applied to AWS** until the accounts exist.

## State and locking

State is stored in S3, one bucket per account, named by the standard (for example `mls-s3-lab-state-001`), with encryption, versioning and locking. The locking mechanism is chosen when the Terraform version is pinned.

## Delivery

| Stage | What runs |
|---|---|
| Pull request | `fmt`, `validate`, an infrastructure scan, and a `plan` attached to the pull request |
| Merge to `develop` | Apply to `dev` |
| Release | Apply to `shared`, `lab` and `dr` after approval on a named environment, using the planned changes that were reviewed |
| Scheduled | Drift detection. A difference raises a ticket |

## Rules

- Provider and module versions are pinned. A module change is a new tagged version; environments move to it by pull request.
- Resources follow the naming and tagging standard in [Repositories and Naming](/Engineering/Repositories-and-Naming). Tags are set by the module, never by hand.
- No secret appears in a variable file, plan output or state-adjacent file. Values come from Secrets Manager.
- Stateful resources (databases, buckets, file systems) have deletion protection and are never replaced silently.
- Each module has a retention rule for anything it creates repeatedly, such as images and snapshots.

## Related

[Helm and Argo CD](/Infrastructure/Helm-and-Argo-CD), [Ansible Roles and Playbooks](/Infrastructure/Ansible-Roles-and-Playbooks), [Bill of Materials](/Architecture/Bill-of-Materials).
