---
description: Run the read-only process audit of an Azure DevOps project and report overall status, trend, what was not covered, and the drift with an ordered fix list
argument-hint: [path to ado-project.yml] [--previous report.json] — defaults to ./ado-project.yml
model: haiku
---

Use the **ado-auditor** agent. Read-only: run `adoctl audit` only.

**Arguments:** $ARGUMENTS

Report overall status and rule, trend, **not covered** checks (the report says nothing about them), drift most severe first, which findings look like real drift and which look like a check bug, and a fix list grouped by who acts. Follow the `ado-doctor` skill.
