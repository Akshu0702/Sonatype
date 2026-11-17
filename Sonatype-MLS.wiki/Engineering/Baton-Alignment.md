# Alignment with the Baton project

MLS follows the project conventions of **Baton** (Azure DevOps project `Baton`, same organisation) and PLATFORM-SPEC-v3, which Baton implements. This page compares the two as read on 14 November 2026 and lists what MLS still has to do. The one deliberate difference is the entitlement model: Baton has Packages and Features; MLS has **Packages, Products and Product Features** ([Entitlement Model](/Packages-and-Feature-Flags/Entitlement-Model)).

**How this was checked:** the Baton wiki (162 pages), its Boards (878 work items), its 14 repositories and 28 pipelines were read. Nothing in Baton was changed.

## Side by side

| Area | Baton | MLS today | To do |
|---|---|---|---|
| **Stack** | Java 21, Spring Boot 3.5.16, Maven, JPA with Hibernate, Flyway, PostgreSQL | Not started. A Node.js prototype was dropped on 13 Nov; a Spring Boot 4.1 build file was drafted and is not used | Use Baton's stack so its code can be reused. Whether to stay on Spring Boot 3.5 (support status to be checked) is a decision |
| **Shared library** | `baton-commons-lib` (package `com.platform.security`): per-issuer token validation, tenant context, row-level-security binding, remote permission checker, `TenantFeatures`, audit event, with isolation tests | `mls-commons-lib` is empty | Start from Baton's library (copy with the origin recorded), add the Product layer to `TenantFeatures`. Keep it compatible so the two can merge later |
| **Tenants and packages** | `baton-tenants-api` holds the tenant registry, provisioning bitmask, Keycloak realm per tenant, branding, packages and entitlements (migration V5), audit outbox | `mls-tenants-api` and `mls-entitlements-api` are empty | Reuse the tenants service; add `products` and `package_products`. Decide whether entitlements stay inside tenants (Baton) or in their own repository |
| **Identity** | One Keycloak realm per tenant, each a distinct issuer; the tenant comes from the issuer registry, never the request | Design only (Keycloak on two hosts in the BOM) | Adopt realm per tenant and the issuer registry |
| **Tenant isolation** | `tenant_id` on every tenant table with row-level security forced, bound by `app.current_tenant` | Designed (ADR-0013); a prototype keyed on user reach was dropped | Use Baton's policy pattern (`docs/rls-policy-pattern.sql`); group reach inside a tenant is an MLS addition on top |
| **Audit** | Audit events through a transactional outbox to the audit service over Kafka, hash-chained | Audit tables designed; Kafka excluded (ADR-0012) | Keep the outbox, replace Kafka with a relay over HTTP; decide whether to keep the hash chain |
| **Repositories** | `baton-<svc>-api`, `baton-<n>-ui-react`, `baton-commons-lib`, `baton-infra` (Ansible, pipelines, templates, `verify/` end-to-end suites, release manifests) | 11 repositories with the same naming and branch policies | Add `verify/`, `releases/` and the shared CI templates to `mls-infra` |
| **Branches** | `feature/US<story>-T<task>`, one task one repository one branch, draft pull request into `develop`, one approval, squash into `develop`, merge commit into `main` | Same | None |
| **Boards** | Agile process: Epic, Feature, Story ("As a ... I ... so that ..."), Task, Test Case, Bug; area path per repository; one task per repository; a Test Case per acceptance criterion linked *Tested By*; a Test Plan per release; a helper script (`baton_work.py start`, `pr`, `status`) | Same hierarchy and area paths; stories not in the "As a" form; Test Cases exist from the kickoff; no Test Plan per release; no helper script | Rewrite stories in the "As a" form as they are refined; create the Test Plan for the first release; write the helper |
| **Wiki** | Sections: Platform Overview (Glossary, Personas), Architecture, Services (one page per service and the library), API Reference, Features, Packages and Feature Flags, Roles, Frontends, Infrastructure, Engineering (CI/CD Plan, Artifact Feed and Tagging, Testing Strategy, Local Development), Operations (Runbooks, Troubleshooting, Known Issues), Decisions, Roadmap | Similar skeleton (about 70 pages) | Add Glossary, Services pages for the platform services and the library, CI/CD Plan, Artifact Feed and Tagging, Testing Strategy, Local Development, Runbooks, Troubleshooting |
| **CI** | One YAML per repository (`azure-pipelines-ci.yml`) calling shared templates in `baton-infra/pipelines/templates` (`ci-java-api`: JDK 21, Maven cache, build the library, PostgreSQL 16 in Docker, `mvn verify`, publish test results, stage the artifact); triggers on `main`, `develop`, feature, bugfix, hotfix and pull requests | CI for the infra repository and security-scan pipelines for the application repositories (drafts); no Java build and test template | Add `ci-java-api` and `ci-ui` templates to `mls-infra`; each service's pipeline extends them |
| **Artifacts** | Azure Artifacts feed `baton-maven` with views `@Local` (snapshots), `@Prerelease` (release candidates), `@Release`; versions `X.Y.Z-SNAPSHOT`, `-rc.N`, release | None | A feed (`mls-maven`) for the jars and libraries; container images and charts go to the registry used by the Kubernetes clusters |
| **CD** | Release pipelines `cd-baton-<svc>` deploy a package *from the feed* to dev; a merge to `develop` creates the dev release; `release/X.Y.Z` builds `-rc.N` and tags it; `promote-baton-release` (approved) adds the same bytes to `@Release`, tags `vX.Y.Z` and opens the pull request to `main`; the tested set is recorded in `releases/X.Y.Z.yml` | Terraform deploy and destroy pipelines with a plan guard and approval; Argo CD designed for the applications | Keep Baton's principles: **build once and promote, deploy from the artifact store not from `main`, the tag is the truth**. Replace "Ansible to a VM" with Argo CD on Kubernetes |
| **Tests** | `test-baton-e2e`, `-security`, `-performance` pipelines; end-to-end suites under `baton-infra/verify`; each check named with its Test Case id | The infra tests use a mocked provider | Create `mls-infra/verify` and the three test pipelines; name checks with Test Case ids |
| **Decisions** | ADR-0001 to 0014: realm per tenant, row-level security as the last line, outbox for audit, permissions from the database, feature flags through packages, internal endpoints on a private network, and more | ADR-0001 to 0013 | Adopt Baton's ADR-0001, 0002, 0004, 0007 and 0012 for MLS with the Product layer added |

## What changed in MLS because of this review

- The drafted Spring Boot 4.1 build and the Node.js and user-reach prototypes are discarded; the migrations, contract and test scenarios are kept.
- The entitlement model is now Package, Product, feature group, feature, with six layers ([Entitlement Model](/Packages-and-Feature-Flags/Entitlement-Model)).
- Boards has stories for adopting the shared library, the CI templates, the feed and promotion, the end-to-end suites and the work helper (feature *Align delivery with Baton*).

## Decisions taken on 14 November 2026

| Question | Decision |
|---|---|
| Spring Boot version | **4.1** (Baton's code is ported from 3.5.16: Spring Security 7, Hibernate 7, Jackson 3) |
| Where entitlements live | **Inside the tenants service**, as in Baton |
| Copy or depend on Baton's library | **Copy**, origin recorded in the repository |
| Audit transport | **Kafka** with the outbox, as in Baton (ADR-0012 amended); Redis stays out |

## Open points

1. Where Kafka runs (Amazon MSK or self-managed), its size and security.
2. Whether Redis is needed for rate limiting (Baton uses it; MLS does not for now).
3. Whether the tamper-evident audit hash chain (Baton ADR-0009) is adopted.
4. ~~When to delete the unused `mls-entitlements-api` repository.~~ Deleted on 15 Nov 2026.
