# ADR-0009: Step Functions as code, with versions and aliases

**Status:** Proposed

## Context
The ticket lifecycle and the other workflows are critical and must be reviewed, tested and rolled back like code.

## Decision
Keep each state machine as an Amazon States Language file in `mls-automation-api`. Deploy through the `step-functions` Terraform module. Publish a state machine version on each deployment and start workflows through an alias, so a release moves the alias and a rollback moves it back.

## Consequences
- Reviewable definitions and a clean rollback.
- Running executions finish on the version they started with.
- Needs the testing approach confirmed when the first state machine is built.
