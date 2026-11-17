# Repository Layouts

What each repository contains, so a new engineer knows where to look. Repository names and branches are in [Repositories and Naming](/Engineering/Repositories-and-Naming).

[[_TOC_]]

**Status legend.** **Built** = running and used. **Partly** = exists, with a stated gap. **Not built** = designed, nothing runs. A plan is never described as behaviour.

## mls-infra

```
mls-infra/
  terraform/
    modules/            reusable modules, versioned by tag
    live/{shared,lab,dev,dr}/
  ansible/
    inventories/<env>/
    group_vars/all/
    roles/
    playbooks/
  helm/
    charts/<app>/       wrapper charts
    values/<env>/<app>.yaml
  argocd/
    projects/
    applicationsets/
    apps/<env>/
  pipelines/
    ci-mls-infra.yml    the CI pipeline
    templates/          shared CI and CD templates
  scripts/
    ci/                 terraform-checks.sh, security-scan.sh
  verify/
    matrix/             Firewall test matrix (YAML)
    runner/             test runner job
  releases/
    X.Y.Z.yml           manifest: the tested version of every component
    X.Y.Z.md            release notes
  compliance/
    controls.yml        control register
  README.md
```

## mls-automation-api

```
mls-automation-api/
  statemachines/        Amazon States Language definitions, one file each
  lambdas/<function>/   one folder per function: source, tests, README
  provisioning-api/     the provisioning service
  cli/                  the mls command line
  openapi/
    provisioning.v1.yaml
  tests/                state machine and integration tests
  scripts/
  README.md
```

See [Step Functions](/Services/Step-Functions) for how state machines are versioned and promoted.

## mls-portal-api

```
mls-portal-api/
  src/
  openapi/
    portal.v1.yaml      the API contract (source of truth)
    iam.v1.yaml         users, groups, roles, scopes
  migrations/           forward-only SQL, V<n>__<description>.sql
  tests/                unit and API tests, including row-level security
  README.md
```

## mls-portal-ui-react

```
mls-portal-ui-react/
  src/
  src/api-client/       generated from openapi/portal.v1.yaml
  e2e/                  browser journeys, one per role
  README.md
```

## mls-fwplugin-bundle

```
mls-fwplugin-bundle/
  bundle/               plugin files as supplied
  manifest.template.json
  scripts/              package and checksum
  tests/                bundle integrity tests
  README.md
```

## mls-commons-lib

```
mls-commons-lib/
  security/             JWT validation, permission check, session binding for row-level security
  audit-client/         the one way every service writes audit events
  tests/
  README.md
```

## Rules

- A repository's README is short and links here and to the wiki.
- Generated code is never edited by hand. The API client is regenerated when the specification changes.
- Database migrations only move forward. A change that cannot run beside the previous version is a major change.
- A pull request that changes a layout updates this page.
