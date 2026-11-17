# Sonatype Managed Lab Wiki

The Sonatype Managed Lab is a dedicated AWS environment (Mumbai primary, Hyderabad DR) where Sonatype Repository Firewall is integrated with Artifactory, Cloudsmith and Harbor repositories and with CI/CD platforms, and where test environments are provisioned on demand. Work is requested, approved, executed and evidenced through a ticket portal (SOW #8).

This wiki documents the lab **as it is built today**, how it is deployed and operated, how we build and release it, and where it is going. It is updated in the same change as the behaviour it describes.

[[_TOC_]]

## Where to start

| If you want to... | Read |
|---|---|
| Understand scope, success criteria, users and what is built | [Managed Lab Overview](/Managed-Lab-Overview) |
| See how the pieces fit together | [Architecture](/Architecture) |
| Know how we are implementing it, and in what order | [Implementation Approach](/Implementation-Approach) |
| Learn how plugin deployment, the ticket lifecycle, provisioning and tests are automated | [Automation](/Automation) |
| Learn how the portal works as a service desk | [Portal and ITSM](/Portal-and-ITSM) |
| See how the Firewall is integrated with each repository and pipeline | [Repositories and Integrations](/Repositories-and-Integrations) |
| Build, name, review and release changes | [Engineering](/Engineering) |
| Know which security standards apply, how they are enforced and where the evidence is | [Security and Compliance](/Security-and-Compliance) |
| See how the design is reviewed against the AWS Well-Architected Framework | [Well-Architected Review](/Architecture/Well-Architected-Review) |
| Find every service and component, the bill of materials and the diagrams | [Services](/Services), [Bill of Materials](/Architecture/Bill-of-Materials), [Infrastructure Diagrams](/Architecture/Infrastructure-Diagrams) |
| Understand how cost is built up, tracked and reviewed | [Total Cost of Ownership](/Architecture/Total-Cost-of-Ownership) |
| Look up the portal API, the data model and the audit design | [API Reference](/API-Reference), [Data Model](/Architecture/Data-Model), [Audit and Logging](/Architecture/Audit-and-Logging) |
| Find the Terraform modules, Ansible roles, Helm charts and Argo CD applications | [Terraform Modules](/Infrastructure/Terraform-Modules), [Ansible Roles and Playbooks](/Infrastructure/Ansible-Roles-and-Playbooks), [Helm and Argo CD](/Infrastructure/Helm-and-Argo-CD) |
| See what is in each repository | [Repository Layouts](/Engineering/Repository-Layouts) |
| Learn what each person can do in the portal: create a ticket, check status, follow up, approve | [Roles](/Roles) |
| Understand how users, groups, roles and scopes control access | [Authentication and Authorization](/Architecture/Authentication-and-Authorization), [IAM](/Services/IAM), [IAM API](/API-Reference/IAM-API) |
| Understand how features are switched on and given to roles by the administrator | [Feature Flags](/Feature-Flags) |
| See how infrastructure and deployment status is monitored, and what alerts exist | [Monitoring and Alerting](/Operations/Monitoring-and-Alerting), [Alert Catalogue](/Operations/Alert-Catalogue), [Dashboards and Status](/Operations/Dashboards-and-Status) |
| Find environments, IaC, secrets and access | [Infrastructure](/Infrastructure) |
| Operate, troubleshoot and see open issues | [Operations](/Operations) |
| Know why a design choice was made | [Decisions](/Decisions) |
| See what is planned and when | [Roadmap](/Roadmap) |

## The lab in one picture

::: mermaid
flowchart LR
  S[Sonatype users<br/>request, approve, review] -->|Site-to-Site VPN| P[Lab portal<br/>React + Node.js]
  M[Motivity engineers] -->|VPN, IAM Identity Center| P
  P --> K[Keycloak<br/>SSO, MFA, 6 roles]
  P --> SF[Step Functions<br/>S1-S9 lifecycle]
  SF --> L[Lambda functions<br/>upload, status, versions, evidence]
  SF --> J[K8s Jobs<br/>rollout, tests]
  J --> E1[EKS Env-1<br/>Artifactory x3]
  J --> E2[EKS Env-2<br/>Artifactory x3 + Harbor]
  E1 --> IQ[IQ Server<br/>Firewall, HA]
  E2 --> IQ
  P --> PR[Provisioning API<br/>hardened Linux + PostgreSQL, TTL]
  PR --> EN[(Ephemeral environments)]
  L --> S3[(S3 evidence and bundles)]
:::

**Status legend.** **Built** = running and used. **Partly** = exists, with a stated gap. **Not built** = designed, nothing runs. A plan is never described as behaviour.
