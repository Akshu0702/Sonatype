# Pipelines

| File | Pipeline name | Runs |
|---|---|---|
| `ci-ado-platform.yml` | `ci-ado-platform` | On pull requests and pushes: lint, tests, plugin structure, YAML parses. Publishes nothing |
| `audit-fortnightly.yml` | `audit-<code>-fortnightly` | Every fortnight (Monday 03:30 UTC, off weeks skip) and on demand: `adoctl audit` for each project in the `projects` parameter |

Create and authorise them after the pull requests are merged: `python scripts/create_audit_pipeline.py --manifest manifests/<project>.yml --service-connection <code>-ado-audit-sc [--apply]`.

## The audit run

1. **Is this an audit week?** The schedule ticks every Monday; weeks are counted from the anchor Monday in the YAML and odd weeks skip (the run succeeds, no report). Manual runs always run. Why not ISO week parity: at the end of a 53-week year, ISO weeks 53 and 1 are both odd and three weeks would pass. Alternative if a fixed calendar is acceptable: `cron: "30 3 1,15 * *"` and no skip step.
2. **Credential.** With `auditServiceConnection` set (for example `mls-ado-audit-sc`), an `AzureCLI@2` step gets a short-lived Microsoft Entra token for Azure DevOps (`az account get-access-token --resource 499b84ac-1321-427f-aa17-267ca6975798`) and exposes it as the secret variable `ADO_TOKEN`. With it empty, the run falls back to `System.AccessToken`. The run summary says which was used.
3. **Previous report.** `adoctl fetch-previous` takes the `report.json` of the last successful run that has one (skipped runs are passed over). Chosen over a `reports/` branch because it needs no write access to a protected branch and no merge; the cost is that it expires with the run's retention.
4. **Audit and publish.** The Markdown goes to the run summary, and JSON plus Markdown are published as the artifact `audit-report-<project>`. `failOn` (`none` by default) can fail the run on critical or major drift, after the artifact is published.
5. **Optional, off by default:** `publishToWiki` (page `Operations/Process-Audit`) and `openBugs` (one Bug per new critical drift, deduplicated by check id and subject). Both **write**, so they use `System.AccessToken`, never the read-only audit identity.

## Permissions

| Credential | Needs | Why |
|---|---|---|
| The audit identity (`<code>-ado-audit`, through the service connection) | Reader on the project (Boards, repositories, builds, wiki, releases), Reader on the feed | Everything in the audit is a read |
| The pipeline's agent | `python3` with `venv`, and the Azure CLI (for `AzureCLI@2`), on the pool | Install adoctl, get the token |
| The service connection | Authorised for the audit pipeline only | Nobody else can use the identity |
| `System.AccessToken` (fallback, and the two optional steps) | Read on the project; for `publishToWiki` Contribute on the wiki; for `openBugs` create work items in the area | The two optional steps write |

**What the identity cannot read.** Not tested: `System.AccessToken` exists only inside a run, and the audit identity does not exist yet. By design, anything it cannot read is shown under **Not covered** (checks that need rights beyond Reader are the likeliest: reading the feed's permission list, F02; reading the Git tag permissions, RL05; reading release definitions with a build-service token). The first manual run will show the real list; paste it into the accepted-risk register and grant what is justified.
