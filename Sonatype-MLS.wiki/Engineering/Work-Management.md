# Work Management

Azure Boards, project `Sonatype-MLS`.

| Level | Is | Sized to |
|---|---|---|
| Epic | A business outcome | the engagement |
| Feature | A capability a user can use | one to two weeks |
| User Story | One user-visible change with acceptance criteria | one sprint |
| Task | One unit of work in exactly one repository: one branch, one pull request | one to three days |
| Test Case | A repeatable check of one acceptance criterion | |
| Bug | A defect with a regression test case | |

- A story title reads "As a `<who>` I `<do what>` so that `<why>`".
- Each acceptance criterion becomes a Test Case; checks are named with the Test Case id, for example `[TC1234]`.
- Every pull request into `main` or `develop` links a work item.
- Area paths: `Sonatype-MLS\<Component>` per repository.
- A story is closed when its Test Cases pass and its wiki pages are updated.

## Sprints

One-week sprints, taken from the kickoff plan. Iteration paths are `Sonatype-MLS\Sprint N`.

| Sprint | Dates | Focus | Exit |
|---|---|---|---|
| Sprint 1 | 26-30 Oct | Kick-off, requirements, architecture and BOM, licence orders, Sonatype inputs | Gate 1 |
| Sprint 2 | 02-06 Nov | AWS foundation, identity, IQ Server HA, Artifactory Env-1, portal with SSO and RBAC | Demo 1 |
| Sprint 3 | 09-13 Nov | Env-2, Harbor, SaaS repositories, Firewall integration, plugin bundle, S1-S6 | Demo 2 |
| Sprint 4 | 16-20 Nov | CI/CD gates, provisioning API and TTL, test harness MVP, S1-S7 | Demo 3 |
| Sprint 5 | 23-27 Nov | Full test matrix, ITSM features, HA and DR validation, runbooks | Gate 2 |
| Sprint 6 | 30 Nov-04 Dec | Acceptance testing, knowledge transfer, go-live | Go-live |

## Backlog structure

Six epics: Delivery foundation and governance; AWS platform and security; Firewall, repositories and CI/CD; Automation; Portal and ITSM; Validation, operations readiness and go-live. Each epic holds features, user stories with acceptance criteria, one task per repository, and one Test Case per story.

## Area paths

`Sonatype-MLS\Infra`, `Portal-API`, `Portal-UI`, `Automation`, `Plugin-Bundle`, `Commons-Lib`, `Delivery`. A task belongs to the area of the repository it changes; `Delivery` holds non-code work.

## Definition of Done: features

A user-visible capability is not done until it has a scope (its feature id) in the catalogue, a flag (off by default), a default role assignment and its wiki pages updated. See [Adding a Feature Flag](/Feature-Flags/Adding-a-Feature-Flag).
