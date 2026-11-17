# Repositories and Integrations

The Firewall integration for each repository platform and CI/CD platform.

[[_TOC_]]

| Platform | Hosting | Planned | Status |
|---|---|---|---|
| Artifactory Env-1 | Self-hosted on EKS | W2 | Not built |
| Artifactory Env-2 | Self-hosted on EKS | W3 | Not built |
| JFrog Enterprise X / Artifactory SaaS | SaaS, via egress allow-list | W3 | Not built |
| Cloudsmith | SaaS, via egress allow-list | W3 | Not built |
| Harbor | Self-hosted on Env-2 | W3 | Not built |
| AWS CodePipeline / CodeBuild | Managed, in VPC | W4 | Not built |
| GitLab | Self-managed with runners | W4 | Not built |
| Azure DevOps | SaaS with self-hosted agents | W4 | Not built |

## Validation evidence

For every repository and format: block, quarantine, alerts, pipeline stop, policy types (licence, age, vulnerability), audit logs and SSO/RBAC. Evidence is stored by the test harness and assembled for Gate 2.

## Open question

Whether JFrog Enterprise X and Artifactory SaaS are one platform or two (see [Known Issues and Gaps](/Operations/Known-Issues-and-Gaps)).
