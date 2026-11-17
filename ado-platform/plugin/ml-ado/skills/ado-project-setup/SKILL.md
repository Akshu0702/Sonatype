---
name: ado-project-setup
description: Set up an Azure DevOps project from an ado-project.yml manifest following AzureDevOps-Platform-v1 section 12 (areas and sprints, repositories with main and develop, branch policies), with a plan shown and approved before every change. Load when a project manager asks to set up, bootstrap or onboard a project, or to check what is still missing.
---

# Setting up a project from its manifest

The setup is `adoctl`, not memory. Your job is to read the manifest with the person, show the plan, get a yes, and run one step at a time.

## Before anything

1. A manifest exists (`ado-project.yml`, valid, no `<placeholders>` left, no secrets). If not: `adoctl init`, then fill areas and repositories together with the person.
2. A credential is in the environment: `ADO_TOKEN` (a service identity's token, preferred) or `ADO_PAT`. If neither is set, stop and tell the person how to set one in their own shell. Never ask them to paste it into the chat; never print or write it.
3. The project is the manifest's project. `adoctl` refuses any other.

## Order (spec section 12)

| Step | Built in adoctl 0.1 | Command |
|---|---|---|
| 1 Read the manifest | yes | `adoctl plan --step 1` |
| 2 Areas and sprints | yes | `adoctl plan --step 2`, then `adoctl apply --step 2 --apply` |
| 3 Repositories with main, develop, README, template | yes | step 3 |
| 4 Branch policies on main and develop | yes (build validation comes with step 8) | step 4 |
| 5 to 13 Wiki, backlog, templates, pipelines, feed, release pipelines, target, first deployment, report | **no**: do by hand from the setup runbook, then `adoctl audit` | refused with exit code 3 |

## How to run a step

1. Run it **without** `--apply` and show the output as it is. Say what each line will change.
2. Ask for a yes for that step only. A yes for step 2 is not a yes for step 3.
3. Run it with `--apply`. Show the output. Run the same command again without `--apply`: it must say "nothing to do". If it does not, stop and explain.
4. Run `adoctl audit` for the area and report what passed and what did not.

## Never

Set `dryRun` to false, approve or merge a pull request, delete anything, run a project that is not in the manifest, skip the plan, or run two steps on one yes. Step 12 (the first real deployment) needs the delivery lead's go-ahead recorded on the Board and typed by the person in the chat.

Honest report at the end: what was done, what was verified (with the command), and what is not covered (steps 5 to 13 and anything the audit could not read).
