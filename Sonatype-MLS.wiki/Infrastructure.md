# Infrastructure

Environments, infrastructure as code, secrets and access.

[[_TOC_]]

## Environments

| Environment | Purpose | Status |
|---|---|---|
| `dev` | Build and test the portal and automation | Not built |
| `lab` Env-1 | Current-release testing (3-node Artifactory on EKS) | Not built |
| `lab` Env-2 | Upgrade and version matrix (3-node Artifactory + Harbor) | Not built |
| DR (ap-south-2) | Pilot light | Not built |

## Infrastructure as code

Terraform modules per layer with state in S3 and locking. Plan and apply run in a pipeline with approval. Helm values live in `mls-infra`. Environments differ only in their variable files.

## Secrets

AWS Secrets Manager and KMS; External Secrets for EKS. No secret appears in a repository, pipeline definition, log or wiki page.

## Access

| Persona | Path | Authentication |
|---|---|---|
| Sonatype users | VPN, portal | Keycloak and MFA |
| Motivity engineers | VPN, portal, SSM | IAM Identity Center and MFA |
| Automation | Lambda, IRSA | IAM roles, no human credentials |

Motivity administrators have no standing admin rights; a break-glass role is logged.

## Hardening

Images, clusters, accounts and databases follow the baselines in [Hardening Baselines](/Security-and-Compliance/Hardening-Baselines).

## Infrastructure as code

| Page | Covers |
|---|---|
| [Terraform Modules](/Infrastructure/Terraform-Modules) | AWS resources, modules, state, delivery |
| [Ansible Roles and Playbooks](/Infrastructure/Ansible-Roles-and-Playbooks) | Host configuration |
| [Helm and Argo CD](/Infrastructure/Helm-and-Argo-CD) | Workloads on EKS and GitOps |
| [Site-to-Site VPN](/Infrastructure/Site-to-Site-VPN) | Office connectivity through the Transit Gateway, and what the customer must supply |
| [Bill of Materials](/Architecture/Bill-of-Materials) | Every resource and product |
| [Infrastructure Diagrams](/Architecture/Infrastructure-Diagrams) | Architecture and flow diagrams |
