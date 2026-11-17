---
name: ado-work
description: Plan and run work in Azure Boards the AzureDevOps-Platform-v1 way: Epic, Feature, Story ("As a ... I ... so that ..."), Task per repository, Test Cases, branch feature/US<story>-T<task>, definition of done. Load when writing stories or tasks, starting a task, or checking sprint status.
---

# Work in Azure Boards

## Rules (spec section 3)

- **Story title:** "As a `<who>` I `<do what>` so that `<why>`", with acceptance criteria. No criteria, not ready.
- **Task:** a verb and an object, in exactly **one repository**. A story touching three repositories has three tasks. Area path is the repository's component; check the real values before bulk creation.
- **Test Case** per acceptance criterion, linked *Tested By*; automated checks are named `[TC<id>]`.
- **States:** New, Active (the branch exists), Resolved (pull request merged), Closed (Test Cases pass and wiki pages updated).
- A user-visible feature is not done until it has its catalogue entry, a flag off by default, a default role assignment and its wiki pages.

## Commands

| Need | Command |
|---|---|
| Start a task: branch `feature/US<story>-T<task>` from develop, task Active | `adoctl work start <task>` (add `--apply` after the person agrees) |
| Open the pull request | `adoctl work pr <task> --what "..." --verified "..." --not-covered "..." --apply` (see the ado-pr skill) |
| Where is everyone | `adoctl work status [--iteration "Sprint N"]` |

`work start` stops if the task has no single parent story or its area matches no repository. Fix the Board, not the command.

## When asked to create stories or tasks

Draft them in the chat first (title, criteria, one task per repository, area, iteration). Create them only after the person agrees, in one batch, and report the ids.

## Common drift to prevent

Tasks left Active after the pull request merged; two tasks sharing one branch (one task, one branch, one pull request); stories without a Test Case; titles not in the "As a" form.
