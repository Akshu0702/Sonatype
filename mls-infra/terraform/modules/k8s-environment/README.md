# k8s-environment

One environment of the platform on Kubernetes (**UAT** or **Prod**): its network, its EKS cluster and add-on roles, its PostgreSQL database (Amazon RDS), its secrets and keys, and the storage and identity for Harbor, its container registry.
Dev and QA are not here: they run on standalone virtual machines.

**Status: code only. Nothing built from this module exists in AWS.** Validated with Terraform 1.9.8 (`fmt`, `validate`, `test` with a mock provider: 11 tests). The live stacks that call it, `terraform/live/uat` and `terraform/live/prod`, are switched off (`enabled = false`).

## What it builds

| Part | Built from | Notes |
|---|---|---|
| Keys | `kms-keys` | Five customer-managed keys: cluster secrets, volumes, database, registry store, Secrets Manager |
| Network | `network-vpc` | Tiers `nodes`, `data`, `tgw`, `public` (NAT gateways only); one NAT gateway per zone so the nodes can reach Azure DevOps, the feed and the chart repositories (outbound only; there is no public endpoint) |
| Cluster | `eks-cluster`, `eks-addons` | Private API endpoint, access by explicit entries, hardened nodes, add-on roles (EBS CSI, load balancer controller, cluster autoscaler, External Secrets) |
| Database | `rds-postgres` | Encrypted with the database key, private, automated backups, deletion protection, Multi-AZ **in Prod only** (enforced by a check), master password held by RDS in Secrets Manager |
| Registry store | `s3-bucket`, one IAM role | Versioned, encrypted bucket for Harbor's image data; a role that only Harbor's registry service account can assume, limited to that bucket and that key |
| Secrets | `secrets` | Nine **empty** secrets (`mls/<env>/...`). Values are set by hand or by the owning service, never by Terraform |

## One cluster per environment, or a shared cluster

`cluster_mode = "dedicated"` (the default) gives the environment its own VPC and cluster. `"shared"` reuses an existing cluster and separates the environment by namespace; it still builds the environment's own database, secrets, keys and registry store, builds no VPC, cluster or add-ons, and gives the cluster's External Secrets role read access to this environment's secrets.

**Default: separate clusters.** A Kubernetes upgrade, a bad admission rule, a node-level incident or a mistake in UAT must not be able to reach Prod, and UAT is where upgrades are rehearsed first. The price is a second control plane (about $73 a month) and a second set of nodes and NAT gateways. Use `shared` only for a cost-driven UAT inside the Prod cluster, accepting that blast radius; the names, secrets and database are still separate.

## Inputs worth knowing

| Input | Meaning |
|---|---|
| `environment` | `uat` or `prod`. Anything else is refused |
| `kubernetes_version` | No default: a decision. The live stacks propose 1.36 |
| `db.engine_version` | No default: a decision. The live stacks pin 16.15 (PostgreSQL 16 is the platform's assumption) |
| `node_groups`, `db`, `azs` | Sizing. Prod is checked: three zones and a Multi-AZ database, or the plan is refused |

## Not covered

- Never planned against AWS with a Multi-AZ failover test, never applied, never booted. `terraform plan` with read-only credentials against the real account gives 98 resources to add for UAT and 115 for Prod, none changed or destroyed.
- Harbor, Argo CD and the services are installed from Git by Argo CD (see `helm/platform` and `argocd/environments`); this module only builds what they need.
- Keycloak for UAT and Prod is not part of this module.
- The private DNS zone, the certificate and the network range are not decided (see `iam-by-hand.md`).

See `iam-by-hand.md` for the IAM change and the database step, and `sql/create-app-databases.sql` for the database users.
