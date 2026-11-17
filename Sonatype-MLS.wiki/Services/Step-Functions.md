# Step Functions

How the workflows are defined, versioned, tested and released.

[[_TOC_]]

**Status legend.** **Built** = running and used. **Partly** = exists, with a stated gap. **Not built** = designed, nothing runs. A plan is never described as behaviour.

## Where the code lives

`mls-automation-api`, folder `statemachines/`. Each state machine is one Amazon States Language file, reviewed by pull request like any other code. The Terraform module `step-functions` in `mls-infra` deploys a named, versioned definition.

## State machine catalogue

| State machine | Purpose | Status |
|---|---|---|
| `ticket-lifecycle` | S1 to S9: intake, workspace, approval gate, deploy, verify and restart, version check, test, blockers, report | Not built |
| `provision-environment` | Create an environment from a request, with mandatory TTL and tags | Not built |
| `ttl-reaper` | Remove expired environments and record teardown evidence | Not built |
| `upgrade-checks` | Pre-flight and post-flight checks around an Artifactory upgrade | Not built |
| `plugin-rollout` | Bump the bundle version, request the sync, verify every pod, roll back on failure | Not built |

## Versioning and release

| Rule | How |
|---|---|
| Source of truth | The definition file in Git |
| Version of the code | The repository release version (`X.Y.Z`); the manifest lists the version of each state machine |
| Version in AWS | Each deployment publishes a new state machine **version** |
| Which version runs | An **alias** (`dev`, `lab`) points at a version. Callers start the alias, not a version |
| Release | Merge, publish a version, move the `dev` alias; after approval move the `lab` alias |
| Rollback | Move the alias back to the previous version |
| Running executions | They finish on the version they started with |

## Testing

- Each state is tested with mocked task results, using a local emulator or the service's state test feature (the approach is confirmed when the first state machine is built).
- Each Lambda function has unit tests in its own folder.
- An integration test runs the state machine in `dev` with test fixtures.
- Failure paths are tested as carefully as success paths: retry, catch, rollback.

## Rules

- A state machine uses task tokens for human approvals, so the wait costs nothing and survives restarts.
- Every task has a timeout, retries with backoff for transient errors, and a catch that records a blocker.
- Execution names include the ticket number, so history can be found from the ticket.
- Each state machine has its own IAM role with only the permissions its tasks need.
- Execution logging is on. Payloads are kept free of secrets; large data goes to S3 and the payload carries the key.

## Related

[Lambda Functions](/Services/Lambda-Functions), [Automation](/Automation), [ADR-0004](/Decisions/ADR-0004-Step-Functions-For-The-Lifecycle), [ADR-0009](/Decisions/ADR-0009-Step-Functions-As-Code-With-Versions-And-Aliases).
