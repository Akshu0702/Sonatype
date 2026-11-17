---
description: Start a task (branch and Active), open its pull request, or show sprint status, the AzureDevOps-Platform-v1 way
argument-hint: start <task id> | pr <task id> | status [Sprint N]
---

Follow the `ado-work` and `ado-pr` skills with `adoctl work`.

**Request:** $ARGUMENTS

- `start <task>`: show the plan (`adoctl work start <task>`), ask, then add `--apply`.
- `pr <task>`: draft What, Verified and Not covered with the person (honest, under 4000 characters), show the plan, ask, then add `--apply`. You open the pull request; you never approve or merge it.
- `status [iteration]`: `adoctl work status`; summarise stale Active tasks, pull requests waiting for review, red builds on develop.
