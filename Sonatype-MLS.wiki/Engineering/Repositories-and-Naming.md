# Repositories and Naming

Project code: **mls**. Names follow the organisation's Nomenclature sheet, extended for infrastructure.

## Repositories

| Repository | Holds |
|---|---|
| `mls-infra` | Terraform, Helm values, Ansible, pipelines, release manifests, test matrix |
| `mls-portal-api` | **ITSM Product** API: tickets and lifecycle engine |
| `mls-portal-ui-react` | Portal UI |
| `mls-automation-api` | **AWS Automation Product** API: environments, status, provisioning, Lambdas, state machines |
| `mls-fwplugin-bundle` | Firewall plugin bundle packaging |
| `mls-commons-lib` | Shared library: security, row-level-security binding, audit client, entitlement client |
| `mls-gateway-api` | Gateway: edge routing, token pre-check, rate limiting, blocks `/internal/**` |
| `mls-tenants-api` | Tenants: registry, hierarchy, resolution, lifecycle, **and entitlements (packages, Products, features, overrides)** |
| `mls-iam-api` | IAM: users, groups, roles, scopes, trusted issuers |
| `mls-entitlements-api` | **Deleted on 15 Nov 2026**: it was never used (entitlements are in the tenants service) |
| `mls-audit-api` | Audit: entity-change and security-event audit, retention |

## Names

| Kind | Pattern | Example |
|---|---|---|
| API repo | `mls-<svc>-api` | `mls-portal-api` |
| UI repo | `mls-<int>-ui-<framework>` | `mls-portal-ui-react` |
| Feature branch | `feature/US<story>-T<task>` | `feature/US101-T105` |
| Bugfix / hotfix | `bugfix/US<story>-T<task>`, `hotfix/US<story>-<issue>` | `hotfix/US102-loginBug` |
| Release branch | `release/X.Y.Z` | `release/1.0.0` |
| Release candidate tag | `vX.Y.Z-rc.N` | `v1.0.0-rc.1` |
| Release tag | `vX.Y.Z` | `v1.0.0` |
| CI / CD pipeline | `ci-mls-<name>`, `cd-mls-<name>` | `ci-mls-portal-api` |
| Test scenarios | `FTS-<id>`, `RTS-<id>`, `PTS-<id>` | `FTS-101` |

## Cloud resources

Pattern: `<project>-<resource-type>-<environment>-<function>-<id>`, for example `mls-eks-prod-lab-001`. Lower case letters, digits and hyphens only; three-digit id, never reused. One AWS account per environment: `mls-shared`, `mls-lab`, `mls-dev`.

- Keep the project prefix on globally unique names (S3, Key Vault) unless a provider limit forces a short form.
- Functions: `web`, `api`, `db`, `cache`, `broker`, `idp`, `edge`, `llm`, `mgmt`, plus `lab`, `fw` and `store` (added for this project).
- Tags set by automation, never by hand: `project`, `environment`, `function`, `owner` (a team, never a person), `managed-by`, `name-long`, `region`, `data-class`, and `ttl` and `ticket` for provisioned environments.
- Secrets: `mls/<env>/<service>/<purpose>`; KMS alias `alias/mls-<env>-<purpose>`.

## Status of the repositories

**Built.** The six repositories exist in `Sonatype-MLS`, each with a README, `.gitignore`, a pull request template, and `main` and `develop` branches. `develop` is the default branch.

### Branch policies

Applied to `main` and `develop` in every repository, and blocking:

| Policy | Setting |
|---|---|
| Minimum reviewers | 1. The author and the last pusher cannot approve; approvals reset on a new push |
| Comment resolution | All comments resolved before completion |
| Work item linking | A linked work item is required |
| Merge strategy | `develop`: squash only. `main`: merge commit only, so the tagged commit stays reachable |
| File size (repository level) | 10 MB limit |

**Partly.** Build validation is not yet set. It needs the `ci-mls-*` pipelines (Sprint 2 story "CI pipeline skeletons with build validation and secrets scan"). It is added to both branches when each pipeline exists, and this page is updated then.
