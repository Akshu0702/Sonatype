# Implementation Approach

How the lab is delivered, and in what order. This page is updated as the sequence changes.

[[_TOC_]]

## Principles

1. **Delivery foundation first.** Repositories, branch policies, CI and a deploy path exist before the lab is built.
2. **Build once, promote the same bytes.** The plugin bundle and every release are immutable and versioned; promotion adds a version to a feed view and never rebuilds.
3. **Deny by default, enforced structurally.** Portal scopes come from the portal database (users, groups, roles, scopes), not from identity-provider claims. Tenant and ticket data use row-level security. Audit rows record both the acting and the target party.
4. **A retention rule ships with every artifact.** AMIs, ephemeral environments, images and evidence each have a rule in the same change that creates them.
5. **A failed deployment is visible where a person looks.** A failed workflow opens a blocker in the portal.
6. **Gates start in report mode** and become blocking once findings are triaged.
7. **Hardening follows published standards** (CIS, AWS Foundational Security Best Practices, OWASP), and the evidence is produced by the pipeline, not written afterwards. See [Security and Compliance](/Security-and-Compliance).
8. **Designs are reviewed against the AWS Well-Architected Framework** at Gate 2 and quarterly in managed services. See [Well-Architected Review](/Architecture/Well-Architected-Review).

## Delivery foundation

| Item | Standard |
|---|---|
| Project code | `mls` |
| Repositories | `mls-infra`, `mls-portal-api`, `mls-portal-ui-react`, `mls-automation-api`, `mls-fwplugin-bundle`, `mls-commons-lib` |
| Branches | `main` released code only; `develop` integration; `feature/US<story>-T<task>`; `release/X.Y.Z` |
| Pipelines | Multi-stage YAML with environments and approvals |
| Work items | Epic > Feature > Story > Task, one task per repository |
| Naming and tags | See [Repositories and Naming](/Engineering/Repositories-and-Naming) |

## Delivery sequence

| Week | Platform | Automation and portal | Exit |
|---|---|---|---|
| W2 (02-06 Nov) | Landing zone, network, VPN, KMS, secrets. EKS Env-1 + Artifactory. IQ HA | Keycloak and portal in dev. Permissions and audit first. Step Functions skeleton | Demo 1 |
| W3 (09-13 Nov) | Env-2, Harbor, SaaS connectors, Firewall-to-repository integration, image pipeline | **Walking skeleton**: ticket, approval, plugin bundle, rollout, version check, evidence (S1-S6) | Demo 2 |
| W4 (16-20 Nov) | CI/CD with gates, DR pilot light | Provisioning API and TTL reaper, test harness MVP, S1-S7 end to end | Demo 3 |
| W5 (23-27 Nov) | HA failover, restore, DR drill, security scan | Full test matrix, ITSM features, runbooks mirror workflows | Gate 2 |
| W6 (30 Nov-04 Dec) | Acceptance support, defect fixes | Knowledge transfer, operations readiness | Go-live |

Thanksgiving (26-27 Nov) limits Sonatype availability in W5; reviews are held on 23-25 Nov.

**Vertical slices.** One thin path works end to end by Demo 2, then widens. No layer is built completely before the next starts.

## Quality gates and evidence

Each demo and gate has acceptance criteria written first and traced to test cases. Pipelines capture evidence automatically; Gate 2 assembles it from stored runs. Two test suites never run at once, and none runs over a deployment.

## Dependencies on Sonatype

Firewall licences and builds; IQ HA licence confirmation; SAML IdP metadata and named approvers; office network ranges; Firewall test plans; the priority configuration list (hardened Linux images and PostgreSQL versions).

## Related

[Roadmap](/Roadmap), [Known Issues and Gaps](/Operations/Known-Issues-and-Gaps).
