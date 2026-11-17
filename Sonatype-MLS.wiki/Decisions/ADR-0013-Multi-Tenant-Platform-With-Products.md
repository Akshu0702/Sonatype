# ADR-0013: A multi-tenant platform with Products

**Status:** Accepted, 13 November 2026 (decided by the project owner; follows PLATFORM-SPEC-v3).

## Context

The lab portal was designed as one application for one customer. It is to be reused later by other tenants and for other purposes: the ticketing portal (ITSM) and the AWS automation that provisions and reports on environments are different capabilities, with different users, roles and APIs, that must be switched on per customer.

## Decision

1. **A tenant is a wrapper.** The platform holds tenants (organisations) and decides what each can use. It is multi-tenant from the start: every tenant-owned table carries `tenant_id` and row-level security is forced on it, so isolation does not depend on each query remembering a filter.
2. **A Product is the unit a tenant selects.** The first two are **ITSM** and **AWS Automation**. A Product has its own packages, features, APIs, roles and scopes, data, and repository. Deployed to the same tenant, each Product keeps its own APIs and roles.
3. **One entitlement axis, deny by default.** A feature is available to a tenant only when the tenant's override, or else the package of the selected Product, grants it. A new feature nobody has granted is unreachable. This is the feature-flag mechanism (it supersedes the separate flag table in ADR-0010; the administrator's assignment of scopes to roles stays, inside the tenant).
4. **Effective permission = a scope the person's roles grant AND a feature the tenant is entitled to.** Both are read from the platform's own database on every request, never from identity-provider claims.
5. **One service and one repository per platform service**: Gateway, Tenants, IAM, Audit. **Entitlements (packages, Products, features, overrides) live inside the Tenants service**, as in Baton (decided 14 Nov; this replaces the separate `mls-entitlements-api` chosen on 13 Nov). Shared security code, the row-level-security binding, the audit client and the entitlement client live in `mls-commons-lib`, not copied between services. The Products are separate services: ITSM in `mls-portal-api`, AWS Automation in `mls-automation-api`.
6. **Stack:** Java 21, Spring Boot, Maven, Flyway, PostgreSQL with one database per service (spec section 12 and 13).
7. **Not built now:** Discovery (Kubernetes DNS serves this), Events (Kafka is excluded by ADR-0012), Policy, Integrations, Notification and SCIM (later, as needed).

## Addendum, 14 November 2026: the entitlement model follows Baton, with one more layer

Baton (ADR-0007) resolves features through packages and overrides. MLS keeps that model and inserts the Product: **Package, Products, feature groups, features**. A package contains one or more Products; a Product has feature groups; a group has features. Resolution has six layers (product in package, feature default, package groups and features, then tenant overrides of a product, a group and a feature), with overrides that need a reason and may expire, mandatory Platform features, dependencies within a Product, and a ten-second cached answer. See [Entitlement Model](/Packages-and-Feature-Flags/Entitlement-Model). The project follows Baton's conventions for repositories, Boards, CI and CD, as recorded in [Alignment with the Baton project](/Engineering/Baton-Alignment), and its Java stack (Spring Boot, JPA, Flyway).

## Consequences

- The environment status work (contract, tables, status roll-up) belongs to the AWS Automation Product, not to the ITSM portal, and gains `tenant_id`.
- Tenant creation becomes a resumable workflow tracked by a bitmask (spec section 1); a Product is switched on for a tenant by selecting it and a package.
- More repositories, pipelines and databases to stand up (four new service repositories; `mls-entitlements-api` was created on 13 Nov, never used, and deleted on 15 Nov) in a 3-week window; the first release will be thin and the order of work matters (see Roadmap).
- Exact-match tenant resolution only, with regression tests; no fuzzy matching anywhere.
- ADR-0001 (custom portal) is refined, not reversed: the portal is still ours, as the ITSM Product on a multi-tenant platform.

## Addendum, 14 November 2026: decisions that follow

- **Spring Boot 4.1** (Baton is on 3.5.16, so Baton's code is ported, not copied unchanged).
- **Entitlements stay in the Tenants service** (Baton's arrangement).
- **Baton's shared security library is copied** into `mls-commons-lib` with its origin recorded; Baton's tenants service is the starting point for `mls-tenants-api`.
- **Kafka is used** for audit and events (ADR-0012 amended); Redis is not.
