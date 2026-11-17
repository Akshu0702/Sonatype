# mls-audit-api

Entity-change audit and security-event audit, append-only, partitioned by month, with an automatic retention job. Receives events from every service through the shared audit client.

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
