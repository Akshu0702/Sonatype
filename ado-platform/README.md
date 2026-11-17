# ado-platform

The Azure DevOps procedure (`docs/AzureDevOps-Platform-v1.md`), the tool that sets a project up and audits it against that procedure (`adoctl`), the Claude Code plugin that drives the tool (`plugin/ml-ado`), and the fortnightly audit pipeline (`pipelines/audit-fortnightly.yml`).

Status: **Partly**. `adoctl` automates setup steps 2 to 4 (areas and sprints, repositories, branch policies) and the whole audit; steps 5 to 13 of the setup are done by hand from the runbook and are refused by the tool. See each folder's README for what is and is not covered.

| Folder | What it holds |
|---|---|
| `docs/` | The specification (a template: fill the placeholders per project) |
| `adoctl/` | The command line: `init`, `plan`, `apply`, `audit`, `work`, `release dry-run`, `fetch-previous`, `publish-wiki`, `open-bugs`. Python 3.10+, PyYAML only |
| `plugin/ml-ado/` | Skills, two agents, four commands and two guard hooks for Claude Code |
| `pipelines/` | `ci-ado-platform.yml` (lint, tests, structure checks) and `audit-fortnightly.yml` (the scheduled audit) |
| `manifests/` | One `ado-project.yml` per project this repository audits |
| `scripts/` | `check_plugin.py` (plugin structure check) and `create_audit_pipeline.py` (creates and authorises the pipelines after merge) |

## Rules this repository follows itself

Branches `feature/US<story>-T<task>`, one task one pull request into `develop`, squash merge, a linked work item, **Verified** and **Not covered** in every pull request description, nobody approves or merges their own change.

## Credentials

`adoctl` reads its credential only from the environment, in this order: `ADO_PAT` (a personal access token, Basic), `ADO_TOKEN` (a service identity's access token, Bearer: **preferred for automation**), `SYSTEM_ACCESSTOKEN` (a pipeline's token, Bearer). It is held in memory, never written to a file, a report or a log. Never paste a token into a chat, a ticket or the wiki.

## Quick start

```
pip install ./adoctl
adoctl init --code abc --name "My-Project" --organisation MyOrg --delivery-lead "A B" --project-manager "C D" --go-live 2027-01-31 --first-start 2026-12-07
adoctl plan  --manifest ado-project.yml          # describes; changes nothing
adoctl apply --manifest ado-project.yml --step 2 --apply
adoctl audit --manifest ado-project.yml --out audit-out
```
