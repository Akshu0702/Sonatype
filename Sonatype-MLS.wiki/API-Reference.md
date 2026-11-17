# API Reference

The interfaces the lab exposes. Each API has an OpenAPI specification that is the contract; the code is written to match it.

[[_TOC_]]

| API | Audience | Specification | Status |
|---|---|---|---|
| [Portal API](/API-Reference/Portal-API) | The portal UI and approved integrations | `mls-portal-api/openapi/portal.v1.yaml` | Not built |
| [IAM API](/API-Reference/IAM-API) | The portal UI, administrators and internal callers | `mls-portal-api/openapi/iam.v1.yaml` | Not built |
| [Provisioning API](/API-Reference/Provisioning-API) | The portal, the CLI and test automation | `mls-automation-api/openapi/provisioning.v1.yaml` | Not built |

## Conventions for every API

| Topic | Rule |
|---|---|
| Contract first | The OpenAPI file is changed first, in its own reviewed pull request. Code and clients follow |
| Version | In the path (`/api/v1`). A breaking change is a new major version |
| Authentication | Bearer token (JWT) issued by Keycloak; no sessions and no cookies |
| Authorisation | Permissions are computed from the portal database on every request. A missing grant means denied |
| Errors | RFC 9457 problem details (`application/problem+json`): `type`, `title`, `status`, `detail`, `instance`, plus a `requestId` |
| Rejections on a security boundary | A generic, identically shaped error, with no hint of which check failed |
| Pagination | `limit` and `cursor`; responses carry `nextCursor` |
| Idempotency | `POST` operations that start work accept an `Idempotency-Key` header |
| Time | UTC, ISO 8601 |
| Identifiers | UUIDs; human ticket numbers are separate |
| Internal endpoints | Under `/internal`, reachable only from the private network and rejected at the edge with a 404 for any outside caller |
| Tracing | Every response carries `X-Request-Id`, which appears in the audit event and the logs |

## Generating and checking

- The UI client is generated from the specification and never edited by hand.
- CI lints the specification and checks each change for breaking differences against the previous version.
- A contract test calls the running API and compares responses to the specification.
