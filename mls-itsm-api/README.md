# mls-itsm-api

The ITSM Product's own service for the Sonatype Managed Lab (SOW #8): tickets for on-demand lab environments, with approval. Java 21, Spring Boot 4.1. Project code: `mls`.

It runs on its own. It needs only **PostgreSQL**, the **sign-in server (Keycloak)** and **tenants-service**; it does not call the portal API or the IAM service.

## Branches

| Branch | Use |
|---|---|
| `main` | Released code only; never built or deployed |
| `develop` | Integration; builds snapshots |
| `feature/US<story>-T<task>` | One task, one pull request, always against `develop` |
| `bugfix/US<story>-T<task>`, `hotfix/US<story>-<issue>` | Fixes |
| `release/X.Y.Z` | Stabilises one release |

Direct pushes to `main` and `develop` are blocked.

## Documentation

Behaviour, architecture, runbooks and decisions live in the wiki: https://dev.azure.com/ML-AIML-COE/Sonatype-MLS/_wiki/wikis/Sonatype-MLS.wiki

A change is not done until its wiki page is updated.

## What it does

A requester raises a ticket for an environment and uploads the build ZIP. An approver decides. An approved ticket asks the provisioning API for the environment; the ticket then moves on to testing, a report and closure.

```
RAISED -> PENDING_APPROVAL -> APPROVED -> PROVISIONING -> ACTIVE -> TESTING -> REPORTED -> CLOSED
                           \-> REJECTED -> CLOSED
```

Only those moves are allowed (everything else answers 409). Every move and decision is written to the ticket's history (who, when, comment), which the database will not let anyone change or delete.

The contract is `openapi/tickets.v1.yaml`. Each call needs its feature live for the caller's tenant, otherwise it answers 404:

| Call | Feature |
|---|---|
| `GET /api/v1/configurations` | `itsm.tickets.tickets_create_provisioning` |
| `GET /api/v1/tickets` (`?scope=all` for approvers) | `itsm.tickets.tickets_view_own` (and `tickets_view_all` for `scope=all`) |
| `POST /api/v1/tickets` (multipart, with the build ZIP) | `itsm.tickets.tickets_create_provisioning` and `itsm.tickets.tickets_attach` |
| `GET /api/v1/tickets/{id}`, `POST .../refresh` | `itsm.tickets.tickets_view_own` |
| `POST /api/v1/tickets/{id}/transitions` | `itsm.tickets.tickets_transition` |
| `GET /api/v1/approvals` | `itsm.approvals.approvals_queue` |
| `POST /api/v1/tickets/{id}/approve`, `.../reject` | `itsm.approvals.approvals_decide` |

At startup the service checks these keys against the tenants-service catalogue and refuses to start on a misspelt one (outside the `dev`, `local` and `test` profiles).

## Rules worth knowing

- **Tenant isolation.** Every table has row-level security that is forced; the tenant comes from the verified token, written into each transaction. With no tenant the database returns no rows.
- **Approvers.** Who may decide comes from the IAM permission `itsm.approvals.decide` when `platform.iam.url` is set, and from `itsm.approvers` (token subject or user name) for a standalone run before IAM roles exist. A requester can never decide their own ticket.
- **Builds.** A ZIP up to 100 MB (name ends in `.zip`, content type ZIP, first bytes a ZIP header), stored under `<tenant>/<ticket>/build.zip` with its SHA-256 recorded. It is never unpacked here. The local-disk store sits behind `BuildStore`; an S3 store replaces it later.
- **Provisioning.** Called through `ProvisioningClient`. Today a fake answers (nothing is created); the real one will call the provisioning API. Requests are idempotent on the ticket id.

## Configuration

| Setting | Meaning | Default |
|---|---|---|
| `PORT` | listen port | `8082` |
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | the service's own database (Flyway migrates it) | `jdbc:postgresql://localhost:5432/itsm`, `itsm` |
| `TENANTS_URL` | tenants-service (features, catalogue, trusted issuers) | `http://localhost:8081` |
| `ITSM_APPROVERS` | people who may approve, until IAM holds the role | none |
| `ITSM_STORAGE_DIR` | where the local build store writes | `./data/builds` |
| `ITSM_UPLOAD_MAX_BYTES` | largest build ZIP | 100 MB |

The database user must **not** be a superuser: row-level security does not apply to one.

## Build and test

```
COMMONS_DIR=../mls-commons-lib bash scripts/test.sh
```

Needs Docker only (Maven, JDK 21 and a throwaway PostgreSQL run in containers).
