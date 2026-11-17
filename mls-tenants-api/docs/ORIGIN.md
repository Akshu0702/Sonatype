# Origin of this code

This service is a **copy of Baton's tenants service**, ported to Spring Boot 4.1 and reduced to what MLS needs. It is kept close to Baton's so the two can be compared and fixes can travel in both directions.

| | |
|---|---|
| Source repository | `Baton/baton-tenants-api` (Azure DevOps project `Baton`) |
| Source branch and commit | `main` at `0a71b43` (30 October 2026) |
| Copied on | 14 November 2026 |
| Coordinates | Unchanged: `com.platform:tenants-service`, packages `com.platform.tenants.*` |

## What was changed from Baton's version

| Change | Why |
|---|---|
| Spring Boot 3.5.16 changed to **4.1.2**; starters renamed (`webmvc`, `security-oauth2-resource-server`, `flyway`) | Decision of 14 November 2026 |
| `AuditRecorder` and one test use Jackson 3 (`tools.jackson`) instead of Jackson 2 | Spring Boot 4 uses Jackson 3 |
| **Removed** Baton's branding feature (service, controller, `V3__branding.sql`, its test and its security rules) | Not needed by MLS |
| **Removed** the operator overview and activity endpoints, their service, test and `platform.services` settings | Not needed by MLS |
| Migrations renumbered after the removal: `V1` tenants, `V2` audit outbox, `V3` tenant features, `V4` packages and entitlements | No gap in the numbering |
| `V1__tenants.sql` starts with a fallback `uuidv7()` that is created only when the database does not have one | `uuidv7()` is built into PostgreSQL 18 but not 16 or 17, and the database version for MLS is open |
| Added `scripts/test.sh` and this file | MLS conventions |

## Changed after the copy

| Date | Change |
|---|---|
| 2026-11-15 | Baton's AI-governance catalogue and packages replaced by MLS's: a **Product** layer (`products`, `package_products`, `product` overrides), 65 features in 10 groups under `platform`, `itsm` and `automation`, packages `lab-full`, `itsm`, `automation`. `V3` and `V4` were edited in place (nothing was deployed). Limits changed to MLS's. New tests. Keycloak login theme default renamed `mls`. |

## Keeping it in step

| Date | Direction | Baton commit | Note |
|---|---|---|---|
| 2026-11-14 | Baton to MLS | `0a71b43` | First copy, ported to Spring Boot 4.1 |

From here the two have diverged on purpose (Products). A fix in Baton's resolver or package lifecycle needs applying by hand.
