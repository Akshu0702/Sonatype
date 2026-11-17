# Portal API

The portal's HTTP API, version 1, for tickets and the lab. This is the design; the specification file is written first and this page is generated or updated from it. Users, groups, roles, scopes and federation are in the [IAM API](/API-Reference/IAM-API).

[[_TOC_]]

**Status legend.** **Built** = running and used. **Partly** = exists, with a stated gap. **Not built** = designed, nothing runs. A plan is never described as behaviour.

**State: Not built.** Base path `/api/v1`. Contract: `mls-portal-api/openapi/portal.v1.yaml`. Each endpoint requires a **scope** from the [Feature Catalogue](/Feature-Flags/Feature-Catalogue); a scope whose flag is Off is refused for everyone.

## Endpoints

| Method | Path | Purpose | Scope |
|---|---|---|---|
| GET | `/tickets` | List and filter tickets | `tickets.view_own`, or `tickets.view_all` for every ticket |
| POST | `/tickets` | Create a ticket of a type | `tickets.create_<type>` for the type: support, provisioning, change, incident, problem, access |
| GET | `/tickets/{id}` | One ticket | `tickets.view_own` or `tickets.view_all` |
| PATCH | `/tickets/{id}` | Update fields | `tickets.update` |
| POST | `/tickets/{id}/transitions` | Move the ticket to another state | `tickets.transition` |
| POST | `/tickets/{id}/assign` | Assign to a person or a group | `tickets.assign` |
| GET | `/tickets/{id}/events` | The ticket history | `tickets.view_own` or `tickets.view_all` |
| POST | `/tickets/{id}/comments` | Add a comment | `tickets.comment` |
| POST | `/tickets/{id}/watchers` | Follow the ticket | `tickets.watch` |
| POST | `/tickets/{id}/chase` | Ask for an update | `tickets.chase` |
| POST | `/tickets/{id}/escalate` | Escalate along the escalation path | `tickets.escalate` |
| POST | `/tickets/{id}/cancel` | Cancel before work starts | `tickets.cancel` |
| POST | `/tickets/{id}/reopen` | Reopen a closed ticket | `tickets.reopen` |
| POST | `/tickets/{id}/uploads` | Get a presigned upload for a build ZIP | `tickets.attach` |
| POST | `/tickets/{id}/uploads/{uploadId}/complete` | Confirm the upload and record its checksum | `tickets.attach` |
| GET | `/approvals` | The approval queue | `approvals.queue` |
| GET | `/tickets/{id}/approvals` | Approvals on a ticket | `approvals.history` |
| POST | `/tickets/{id}/approvals` | Approve or reject, with a reason | `approvals.decide` |
| POST | `/tickets/{id}/deployments` | Start a plugin rollout | `deployments.start` |
| POST | `/deployments/{id}/rollback` | Roll back | `deployments.rollback` |
| GET | `/plugin-bundles` | Available bundle versions | `bundles.view` |
| POST | `/tickets/{id}/test-runs` | Start a test run | `tests.run` |
| POST | `/test-runs/{id}/retest` | Run again after a fix | `tests.retest` |
| GET | `/test-runs/{id}`, `/test-runs/{id}/results` | A run and its results per matrix case | `tests.view` |
| GET | `/evidence/{id}` | A link to an evidence file | `evidence.view` |
| GET | `/blockers` | List blockers | `blockers.view` |
| POST | `/tickets/{id}/blockers` | Raise a blocker | `blockers.manage` |
| PATCH | `/blockers/{id}` | Reclassify, reassign, resolve | `blockers.manage` |
| GET | `/environments` | Lab and provisioned environments within the caller's reach | `environments.view` |
| GET | `/environments/{id}/versions` | Artifactory and plugin versions | `environments.versions` |
| POST | `/environment-requests` | Request a provisioned environment | `environments.request` |
| POST | `/environments/{id}/extend` | Extend the TTL | `environments.extend` |
| DELETE | `/environments/{id}` | Tear down | `environments.manage` |
| GET | `/status/overview` | Health of every lab component and environment | `status.overview` |
| GET | `/status/deployments` | Running and recent rollouts, with versions and results | `status.deployments` |
| GET, POST, PATCH | `/changes` | Change records | `changes.manage` |
| GET | `/incidents` | Incidents | `incidents.view` |
| POST, PATCH | `/incidents` | Open and update incidents | `incidents.manage` |
| GET, POST, PATCH | `/problems` | Problem records | `problems.manage` |
| GET | `/sla/policies` | SLA policies | `sla.view` |
| GET | `/kpi/summary` | KPI figures for the dashboard | `kpi.view` |
| GET | `/reports` | Reports | `reports.view` |
| POST | `/reports` | Generate a report for a period | `reports.generate` |
| POST | `/reports/{id}/signoff` | Sign off a report | `reports.signoff` |
| GET | `/cmdb/items` | Configuration items | `cmdb.view` |
| PATCH | `/cmdb/items/{id}` | Correct an item | `cmdb.edit` |
| GET | `/audit/events` | Search audit events | `audit.view` |
| GET | `/audit/verify` | Verify the audit chain | `audit.verify` |
| GET | `/notifications` | The user's notifications | `notifications.view` |
| PUT | `/notifications/preferences` | Choose channels | `notifications.preferences` |

Internal endpoints (private network only): `/internal/executions/{id}/callback` (the workflow reports a task result), `/internal/alarms` (an alarm opens an incident), and the IAM lookups in the [IAM API](/API-Reference/IAM-API).

## Who holds what

The default assignment of scopes to roles is in [Role Feature Assignment](/Feature-Flags/Role-Feature-Assignment). It is held in the database, not in code, and the portal administrator can change it.

## Example specification

The excerpt below shows the style; the real file lives in the repository.

```yaml
openapi: 3.1.0
info:
  title: Sonatype Managed Lab Portal API
  version: 1.0.0
servers:
  - url: https://portal.lab.internal/api/v1
security:
  - bearerAuth: []
paths:
  /tickets:
    post:
      operationId: createTicket
      summary: Create a ticket
      description: Requires the scope tickets.create_<type> for the ticket type.
      x-required-scope: tickets.create_support
      parameters:
        - in: header
          name: Idempotency-Key
          schema: { type: string }
      requestBody:
        required: true
        content:
          application/json:
            schema: { $ref: '#/components/schemas/TicketCreate' }
      responses:
        '201':
          description: Created
          content:
            application/json:
              schema: { $ref: '#/components/schemas/Ticket' }
        '403':
          $ref: '#/components/responses/Problem'
components:
  securitySchemes:
    bearerAuth: { type: http, scheme: bearer, bearerFormat: JWT }
  schemas:
    TicketCreate:
      type: object
      required: [type, title]
      properties:
        type: { type: string, enum: [provisioning, support, change, incident, problem, access] }
        title: { type: string, maxLength: 200 }
        description: { type: string }
    Ticket:
      allOf:
        - $ref: '#/components/schemas/TicketCreate'
        - type: object
          properties:
            id: { type: string, format: uuid }
            number: { type: string }
            status: { type: string }
            createdAt: { type: string, format: date-time }
  responses:
    Problem:
      description: Error
      content:
        application/problem+json:
          schema:
            type: object
            properties:
              type: { type: string }
              title: { type: string }
              status: { type: integer }
              requestId: { type: string }
```

Each operation carries an `x-required-scope` entry, so a contract check can confirm that the code declares the same scope as the specification.

## Related

[API Reference](/API-Reference), [IAM API](/API-Reference/IAM-API), [Data Model](/Architecture/Data-Model), [Audit and Logging](/Architecture/Audit-and-Logging), [ADR-0008](/Decisions/ADR-0008-Spec-First-OpenAPI-For-The-Portal-API).
