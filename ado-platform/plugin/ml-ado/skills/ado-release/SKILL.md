---
name: ado-release
description: Release management the AzureDevOps-Platform-v1 way: cd pipelines deploy from the feed, no trigger, a named approver, dryRun true by default, promotion of the same bytes, rollback as an older package. Load when creating a release, running a dry run, promoting, or when anyone mentions dryRun or deploying.
---

# Releases

## Rules (spec section 9)

- `cd-<code>-<name>` deploys **from the feed**, never from a rebuild. **No trigger**; a named person approves before the first environment.
- Variable `dryRun` is `true` by default. A dry run downloads and checks the artifact, prints its checksum, and makes no cloud call.
- **You never set `dryRun` to false.** That is the delivery lead's decision, recorded on the Board, and the person types the word go-ahead with the definition name in this chat. A hook blocks the change without it. Do not look for another way.
- Promotion adds the same bytes to the next feed view and tags the commit; the tag is the truth. Rollback is a release of the previous version from the feed.

## Dry run

```
adoctl release dry-run <definition>          # prints the plan
adoctl release dry-run <definition> --apply  # creates the release; it then waits for the approver
```
It refuses if `dryRun` is not `true` or the definition has a trigger. It does not approve anything: tell the person where to approve it.

## Before a real deployment (the person decides, you prepare)

Dry run passed with the checksum shown; the deployment target is reachable; the settings file exists on the host; rollback tested; the go-ahead is on the Board. Then stop and wait for the person to type the go-ahead.

## Report

State which release was created, its dry-run output, what the real run would do, and what was not exercised (the cloud path, the real services).
