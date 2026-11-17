# Architecture

How the lab is put together. The Gate 1 deck is the approved reference; this page is the working summary.

[[_TOC_]]

## Regions and access

Mumbai (ap-south-1) is primary across three availability zones. Hyderabad (ap-south-2) is a pilot-light DR region. Access is by Site-to-Site VPN only (IPsec, two tunnels per site, BGP). There are no public endpoints.

## Network

| VPC | Primary CIDR | DR CIDR |
|---|---|---|
| Shared services | 10.40.0.0/20 | 10.50.0.0/20 |
| Lab Env-1 (EKS) | 10.41.0.0/20 | 10.51.0.0/20 |
| Lab Env-2 (EKS) | 10.42.0.0/20 | 10.52.0.0/20 |

Office networks must not overlap 10.40.0.0/13 or 10.48.0.0/13. Env-1 and Env-2 cannot reach each other; automation reaches both.

## Components

| Component | Where | Status |
|---|---|---|
| Portal (React + Node.js), Keycloak | Shared services, EC2 x2 each | Partly |
| IQ Server (Firewall) | Shared services, EC2 x2 + EFS | Not built |
| EKS lab-env1, lab-env2 | Artifactory x3 each; Harbor on Env-2 | Not built |
| RDS PostgreSQL | Multi-AZ; Artifactory x2, portal, Keycloak; IQ on its own instance | Not built |
| ELK, CloudWatch, SNS | Shared services | Not built |
| CI/CD (GitLab, runners, ADO agents) | Shared services | Not built |
| S3, ECR, Secrets Manager, KMS | Account-wide | Not built |

## Disaster recovery

Region failure: promote RDS replicas, scale EKS in ap-south-2, fail over DNS. Targets are estimates until the DR drill sets the actual values.

## Security

Keycloak SSO with MFA; AWS admins through IAM Identity Center; one IAM role per Lambda; KMS customer keys; Secrets Manager; private EKS endpoint; GuardDuty, Security Hub, Config and CloudTrail.

## Related

[Well-Architected Review](/Architecture/Well-Architected-Review), [Implementation Approach](/Implementation-Approach), [Infrastructure](/Infrastructure), [Decisions](/Decisions).

## Detail pages

| Page | Covers |
|---|---|
| [Bill of Materials](/Architecture/Bill-of-Materials) | Every AWS resource and software product |
| [Total Cost of Ownership](/Architecture/Total-Cost-of-Ownership) | Cost lines, drivers, tracking and review (no amounts) |
| [Infrastructure Diagrams](/Architecture/Infrastructure-Diagrams) | Deployment, traffic, delivery, plugin rollout, ticket lifecycle, provisioning, DR |
| [Data Model](/Architecture/Data-Model) | The portal database |
| [Authentication and Authorization](/Architecture/Authentication-and-Authorization) | Sign-in, users, groups, roles, scopes |
| [Audit and Logging](/Architecture/Audit-and-Logging) | Audit events, log sources, retention |
| [Well-Architected Review](/Architecture/Well-Architected-Review) | Design review against the six pillars |
| [Services](/Services) | Every service and component |
| [API Reference](/API-Reference) | Portal and provisioning APIs |
| [Monitoring and Alerting](/Operations/Monitoring-and-Alerting) | Signals, dashboards, alerts, status views |
