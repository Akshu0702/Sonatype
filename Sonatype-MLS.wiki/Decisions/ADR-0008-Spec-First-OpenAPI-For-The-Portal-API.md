# ADR-0008: Spec-first OpenAPI for the portal API

**Status:** Proposed

## Context
The portal UI, test automation and possibly Sonatype integrations depend on the portal API. Drift between documentation and behaviour causes defects.

## Decision
Write and review the OpenAPI specification first. Generate the UI client from it, lint it in CI, check each change for breaking differences, and contract-test the running API against it. The wiki page is generated from or checked against the specification.

## Consequences
- One source of truth for the contract.
- A breaking change is visible in review.
- The specification needs an owner and review discipline.
