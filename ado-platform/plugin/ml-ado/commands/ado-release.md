---
description: Create a dry-run release of a cd-* pipeline (never a real deployment) and explain what the approver sees
argument-hint: <cd pipeline name, for example cd-mls-portal-api>
---

Follow the `ado-release` skill.

**Definition:** $ARGUMENTS

Run `adoctl release dry-run <definition>` and show the plan; after the person agrees run it with `--apply`. It refuses a definition whose `dryRun` is not `true` or that has a trigger. Do not set `dryRun` to false and do not approve the release: a real deployment needs the delivery lead's go-ahead, typed by the person in this chat.
