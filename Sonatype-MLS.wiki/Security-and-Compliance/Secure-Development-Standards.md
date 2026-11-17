# Secure Development Standards

What every change to the portal, the automation and the infrastructure code must meet.

[[_TOC_]]

## Targets

| Area | Standard | Target |
|---|---|---|
| Web and API behaviour | OWASP ASVS | Level 1 for every service; Level 2 for the portal API, the provisioning API and the Keycloak configuration |
| API authorisation | OWASP API Security Top 10 | An authorisation test suite runs in CI for the portal and provisioning APIs |
| Access model | Deny by default | A capability is unavailable unless a role or grant provides it |

## Design rules

- Scopes are computed from the portal database (users, groups, roles, scopes). Identity-provider claims prove who a person is; they never grant a scope.
- Tenant and ticket data are protected by row-level security as well as by query filters, so a forgotten filter returns nothing instead of everything.
- Internal endpoints are reachable only from the private network.
- Every state-changing action writes an audit record carrying the acting and the target party.
- Secrets are never written to code, configuration, logs, tickets or the wiki. Logs mask credentials and tokens before they are indexed.
- A cache exists only if a test proves the request path reads it.
- No disabled or commented-out security code is left in a repository: finish it or delete it.

## Checks in the pipeline

Cheapest first:

| Order | Check | Tool | Mode |
|---|---|---|---|
| 1 | Build and unit tests | Language test runner | Blocking |
| 2 | API tests against a real database, including row-level security | Test suite | Blocking |
| 3 | Secrets in the diff and history | gitleaks | Report, then blocking |
| 4 | Static code analysis | Semgrep | Report, then blocking |
| 5 | Dependency and container image vulnerabilities | Trivy | Report, then blocking |
| 6 | Infrastructure-as-code misconfiguration | A policy scanner on Terraform and Helm | Report, then blocking |
| 7 | Publish | Feed | After 1 to 6 |

Results are published as SARIF and attached to the build. A repository moves a check from report to blocking by setting its enforcement flag, after its findings are triaged.

## Pull requests

The pull request template carries the checklist: linked work item, tests, wiki page updated, no secrets, and an independent review that states what it did not cover. See [Branching and Release](/Engineering/Branching-and-Release).

## Related

[Security and Compliance](/Security-and-Compliance), [Evidence and Exceptions](/Security-and-Compliance/Evidence-and-Exceptions).
