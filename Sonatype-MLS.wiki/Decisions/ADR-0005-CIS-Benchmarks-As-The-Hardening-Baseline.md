# ADR-0005: CIS benchmarks as the hardening baseline

**Status:** Proposed

## Context
The lab must show MFA, hardening, vulnerability scanning and security controls (SOW #8). Provisioned test environments and the lab's own hosts need one baseline that can be checked automatically and explained to Sonatype's reviewers.

## Decision
Use the CIS Benchmarks as the hardening baseline: the Linux distribution benchmark (Level 1 server profile), the Amazon EKS benchmark, the AWS Foundations benchmark and the PostgreSQL benchmark, with AWS Foundational Security Best Practices through Security Hub. Pin each benchmark version in the control register. Record every deviation as an exception with an owner and an expiry. Start checks in report mode and make them blocking after triage.

## Consequences
- One recognised yardstick, with automated scans and ready evidence for Gate 2.
- The Linux distribution decision affects which CIS hardening is available, so it must be settled early.
- Level 1 may leave some hardening to later; Level 2 controls are added by decision, not by default.
- Benchmark versions need an owner who reviews new releases.
