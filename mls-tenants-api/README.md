# mls-tenants-api

Tenant registry and hierarchy, vanity-URL and domain resolution (exact match only), tenant lifecycle, provisioning as a resumable bitmask, and which Products a tenant has selected.

Part of the Sonatype Managed Lab (SOW #8), a multi-tenant platform with Products. Project code: `mls`.
Built to PLATFORM-SPEC-v3: Java 21, Spring Boot, Maven, Flyway, forced row-level security by tenant, deny-by-default entitlements.

## Branches

| Branch | Use |
|---|---|
| `main` | Released code only; never built or deployed |
| `develop` | Integration; builds snapshots and deploys to dev |
| `feature/US<story>-T<task>` | One task, one pull request, always against `develop` |
| `bugfix/US<story>-T<task>`, `hotfix/US<story>-<issue>` | Fixes |
| `release/X.Y.Z` | Stabilises one release |

Direct pushes to `main` and `develop` are blocked. Open a draft pull request linked to your task.

## Documentation

Behaviour, architecture, runbooks and decisions live in the wiki: https://dev.azure.com/ML-AIML-COE/Sonatype-MLS/_wiki/wikis/Sonatype-MLS.wiki

A change is not done until its wiki page is updated.

## Status

**Baton's tenants service on Spring Boot 4.1 with MLS's Product layer and catalogue. 59 tests pass locally against PostgreSQL 16 and 18; not deployed; not yet built by a pipeline.** See `docs/ORIGIN.md` for where it came from and what changed.

It holds the **tenant registry** (exact-match resolution by host, tenant code, domain or name, with no fuzzy matching), **issuer lookup** for the other services, **resumable provisioning** of a tenant's Keycloak realm, sign-in client and first administrator (a bitmask records which steps are done), **tenant lifecycle** (suspend, activate, offboard), the **audit outbox** relayed to Kafka, and **entitlements**: Products (platform, ITSM, AWS Automation), feature groups, features, versioned packages and per-tenant overrides, resolved in layers (`docs/ENTITLEMENTS.md`).

```
COMMONS_DIR=../mls-commons-lib bash scripts/test.sh     # build and test against a throwaway PostgreSQL 16 (needs Docker)
PGIMAGE=postgres:18 COMMONS_DIR=../mls-commons-lib bash scripts/test.sh
```

## Not covered yet

- **Limits are not seeded** (every limit reads as unlimited) and none of the 65 features is enforced yet; the project owner sets limits and package names.
- **The feature catalogue is a proposal**: the 65 portal permissions grouped under the two Products; no feature declares a dependency yet.
- Not deployed; no container image is built or published; no pipeline builds it yet.
- The library `platform-security-commons` is not in a package feed, so building needs `COMMONS_DIR`.
- The Kafka relay was not run against a real broker; it is switched off in the tests.
- Keycloak provisioning was tested against a fake Keycloak only.

## Build pipeline

`pipelines/ci-mls-tenants-api.yml` (pipeline `ci-mls-tenants-api`) runs on the `mls-agents` pool for pull requests and for pushes to `develop` and `release/*`. It checks out `mls-commons-lib` (develop) beside this repository, builds it into the Maven cache and then runs `scripts/test.sh` (Maven, JDK 21, PostgreSQL 16 in containers). Until the library is published to a package feed, a change to the library is picked up here only after it is merged to its `develop`.
