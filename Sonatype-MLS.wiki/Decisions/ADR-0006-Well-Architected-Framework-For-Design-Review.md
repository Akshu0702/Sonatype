# ADR-0006: AWS Well-Architected Framework for design review

**Status:** Proposed

## Context
The lab is a long-lived managed service on AWS. The design needs a recognised way to review it across reliability, security, cost and operations, and to repeat that review as it changes.

## Decision
Review the lab against the six pillars of the AWS Well-Architected Framework, using the Well-Architected Tool, with a named owner per pillar. Track high and medium risks as work items tagged `war-risk`. Review at Gate 2 against the as-built system and every quarter in managed services.

## Consequences
- A shared language for trade-offs and a visible improvement plan.
- Pillar owners must be named and given time each quarter.
- The review depends on the as-built system, so it cannot be completed before the environments exist.
