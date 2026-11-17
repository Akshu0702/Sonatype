# ml-ado

Claude Code plugin for the Azure DevOps procedure in `docs/AzureDevOps-Platform-v1.md`. It does not hold the REST knowledge itself: that lives in the `adoctl` command line (same repository, `adoctl/`), which is tested. The plugin supplies judgement, asks for approval, and keeps the rules.

## Install

1. `pip install ./adoctl` (Python 3.10 or newer).
2. Add the entry in `marketplace-entry.json` to the marketplace's `.claude-plugin/marketplace.json`, then install `ml-ado` the same way as `ml-specs`.
3. Give the session a credential **in the environment only**: `ADO_TOKEN` (a service identity's access token, preferred), else `ADO_PAT`. Never paste a token into the chat.

## What is in it

| Kind | Name | Use |
|---|---|---|
| Skill | `ado-project-setup` | From a manifest to the first dry-run release (spec sections 2 and 12) |
| Skill | `ado-work` | Stories, tasks, branches, definition of done |
| Skill | `ado-pr` | A pull request description with Verified and Not covered; merge order |
| Skill | `ado-release` | Dry run, approval, promotion, rollback |
| Skill | `ado-wiki` | Page template and status labels |
| Skill | `ado-doctor` | Read the audit and turn drift into a fix list |
| Agent | `ado-bootstrapper` | Runs `adoctl plan`, shows it, asks, then `apply` one step at a time |
| Agent | `ado-auditor` | Read-only; runs `adoctl audit` and writes the drift report |
| Command | `/ado-setup`, `/ado-audit`, `/ado-work`, `/ado-release` | Entry points |
| Hook | `token-guard` | Blocks writing a token-shaped string into a file or a commit |
| Hook | `dryrun-guard` | Blocks setting `dryRun` to false unless you typed the go-ahead |

## Rules the plugin keeps

- Describe first, change second: every `adoctl` command that changes something is shown without `--apply` before it is run with it, and a person says yes.
- It never approves or merges a pull request, never sets `dryRun` to false, never deletes, never runs a project that is not in the manifest.
- It never asks for, prints, stores or logs a token.

## The go-ahead

`dryrun-guard` looks for the word `go-ahead` in a message **you** typed in this session (for example: `go-ahead dryRun=false cd-mls-portal-api`). A message the assistant wrote does not count.
