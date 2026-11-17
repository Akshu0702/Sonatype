# mls-commons-lib

Shared library: security (JWT, tenant context, row-level security session) and the audit client.

Part of the Sonatype Managed Lab (SOW #8). Project code: `mls`.

## Branches

| Branch | Use |
|---|---|
| `main` | Released code only; never built or deployed |
| `develop` | Integration; builds snapshots and deploys to dev |
| `feature/US<story>-T<task>` | One task, one pull request |
| `bugfix/US<story>-T<task>`, `hotfix/US<story>-<issue>` | Fixes |
| `release/X.Y.Z` | Stabilises one release |

Direct pushes to `main` and `develop` are blocked. Open a draft pull request linked to your task.

## Documentation

Behaviour, architecture, runbooks and decisions live in the wiki: https://dev.azure.com/ML-AIML-COE/Sonatype-MLS/_wiki/wikis/Sonatype-MLS.wiki

A change is not done until its wiki page is updated.

## Status

**Baton's shared security library, ported to Spring Boot 4.1 and made Product-aware. 46 tests pass against PostgreSQL 16; built by the `ci-mls-commons-lib` pipeline.** See `docs/ORIGIN.md` for where it came from and what changed.

| Piece | What it does |
|---|---|
| `DynamicIssuerAuthenticationManagerResolver` | Validates tokens from any tenant's realm; reads the issuer (size-limited, unverified) only to choose whose keys to verify against |
| `TrustedIssuerRegistry` | The seam a service implements to say which issuers it trusts; exact match only |
| `PlatformSecurity.configure(http, registry)` | One call that wires stateless bearer security and the tenant context into a service |
| `TenantContext`, `TenantContextFilter` | The tenant of the current request, set from the issuer registry, never from the request |
| `RlsConnectionProvider` (auto-configured) | Sets `app.current_tenant` on each connection and clears it before the connection returns to the pool |
| `RemotePermissionChecker` | Asks the IAM service what a caller may do |
| `TenantFeatures` (auto-configured) | The caller-side view of which features and Products are live for a tenant |
| `@RequiresFeature` | An API that answers 404 unless the tenant has the feature live |
| `FeatureCodeStartupCheck` | At startup, checks every feature key the code uses against tenants-service's catalogue; fails the start outside dev, local and test (`platform.features.check` = fail, warn or off) |
| `docs/rls-policy-pattern.sql` | The row-level-security policy every tenant table uses |

```
bash scripts/test.sh          # build and test against a throwaway PostgreSQL 16 (needs Docker)
```

## Not covered yet

- The key check finds literal keys only (`@RequiresFeature` and `@features.on('...')` in `@PreAuthorize`); a key built at run time is not seen.
- Not published to a feed, so no service can depend on it yet.
- The audit outbox and Kafka relay (ADR-0012, amended) are not in this library yet.
