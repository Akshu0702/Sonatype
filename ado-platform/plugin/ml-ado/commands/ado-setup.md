---
description: Set up an Azure DevOps project from its ado-project.yml (areas and sprints, repositories, branch policies), one approved step at a time
argument-hint: [path to ado-project.yml] — defaults to ./ado-project.yml
---

Use the **ado-bootstrapper** agent to set up the project in the manifest.

**Manifest:** $ARGUMENTS (if empty, `ado-project.yml` in the current folder)

Follow the `ado-project-setup` skill: plan each step without `--apply`, show it, ask, apply that one step, verify it is idempotent, run the audit for it. Credentials come from the environment only (`ADO_TOKEN`, else `ADO_PAT`); never ask for one in the chat. Steps 5 to 13 are not automated: list them as hand-over items.
