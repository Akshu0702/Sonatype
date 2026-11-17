# ADR-0004: Step Functions orchestrates the ticket lifecycle

**Status:** Proposed

## Context
Lambda has a 15-minute limit and no native waits, approvals or retries, but a rollout, version check and rollback can take longer.

## Decision
Run the S1-S9 lifecycle as a Step Functions state machine with an approval gate. Lambda functions and Kubernetes Jobs are the workers.

## Consequences
- Waits, retries and rollback states are built in; execution history is audit evidence.
- A new service to operate and secure.
