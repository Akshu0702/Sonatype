# adoctl

Set up, work in and audit an Azure DevOps project against `docs/AzureDevOps-Platform-v1.md`. Python 3.10+, PyYAML only. **Read-only unless `--apply` is given. It never deletes anything.**

```
pip install ./adoctl            # from the repository root
adoctl --help
```

| Command | What it does | Changes anything? |
|---|---|---|
| `init` | Writes `ado-project.yml` from arguments (or asks). Will not overwrite without `--force` | a local file |
| `plan`, `apply [--step N] [--apply]` | Setup steps 2 to 4: areas and sprints, repositories with `main`, `develop` and the starter files, policies on `main` and `develop`. Idempotent. Steps 5 to 13 are refused (exit code 3) | only with `--apply` |
| `audit [--previous report.json] [--fail-on critical\|major]` | Every check of spec section 15 that the API allows. Writes `report.json` and `report.md` | no |
| `work start\|pr\|status` | Branch and Active state, pull request with Verified and Not covered, sprint status | `start` and `pr` only with `--apply` |
| `release dry-run <cd definition>` | Creates a dry-run release; refuses unless `dryRun` is `true` and there is no trigger. Never approves | only with `--apply` |
| `fetch-previous`, `publish-wiki`, `open-bugs` | Helpers of the fortnightly pipeline (previous report, wiki page, one Bug per new critical drift) | `publish-wiki` and `open-bugs` only with `--apply` |

A project that is not in the manifest is never touched (`--project` must equal the manifest's project).

## Credential

From the environment only, in this order: `ADO_PAT` (Basic), `ADO_TOKEN` (Bearer; a Microsoft Entra access token of a service identity, resource `499b84ac-1321-427f-aa17-267ca6975798`; preferred), `SYSTEM_ACCESSTOKEN` (Bearer). An unexpanded pipeline macro such as `$(ADO_TOKEN)` is ignored. The token is scrubbed from every message, never written to disk, never in a report.

## Audit report

`report.json`: per check `id`, `area`, `title`, `status` (`pass`, `warn`, `fail`, `not-applicable`), `severity` (`critical`, `major`, `minor`), `subject`, `evidence`, `fix`, `rule` (the spec section), `blind`. `report.md`: overall status, per-area table, drift most severe first, **Not covered**, trend.

- **Overall status.** Red: any failed critical check, or ten or more failed major checks. Amber: not red, and any failed check, warning, or check that is not covered. Green: nothing failed, no warning, every check ran.
- **Not covered.** If the credential cannot read something (HTTP 401 or 403, or the sign-in page Azure DevOps returns for an unaccepted credential), the check is reported as `NOT COVERED` and listed in its own section. It is neither a pass nor a failure. Other errors are `COULD NOT RUN`. If the credential cannot read the project's repositories at all, the audit stops with an error (exit code 2): there is nothing to report on.
- **Trend.** With `--previous`, the report compares failed checks, critical and major counts, new drift and resolved drift.
- **Secrets scan.** Reports file, line and the kind of pattern. It never reports, logs or stores the value.

## Tests

`pytest adoctl` (a fake Azure DevOps API, every check on its pass and fail path, dry-run default, refusal without a manifest, the token never in any output). `ruff check`.
