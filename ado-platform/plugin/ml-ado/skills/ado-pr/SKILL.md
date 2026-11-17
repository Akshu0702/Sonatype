---
name: ado-pr
description: Write and open a pull request the AzureDevOps-Platform-v1 way: one task, targets develop, linked work items, a description with What, Verified and Not covered under 4000 characters, merge order by dependency, never approve or merge. Load when opening, describing or ordering pull requests.
---

# Pull requests

## Rules (spec 5.3)

1. One task, one repository, one pull request, targeting **develop**, linked to the task and its story. Never stack on a feature branch.
2. Tests and the wiki page are in the same pull request.
3. Description under **4,000 characters** with: **What** (task and story ids), **Verified** (the commands run, the counts, what the result proved), **Not covered** (what was not run, what is a stand-in, what is left), and the checklist.
4. **Nobody approves or merges their own pull request, and you never approve or merge any.** Open it and tell the person it is ready for their review.
5. **Merge order follows dependency:** shared templates and library, then services, then automation, then user interface. A pull request that uses a file from another one is merged after it. When you open several, say the order in the first line of each.
6. After a dependency merges, the waiting pull requests' build validation may need requeuing.

## Open it

```
adoctl work pr <task> --what "<one paragraph>" --verified "<commands and counts>" --not-covered "<honest gaps>"
```
That prints the plan (title, source, target, length). Show it; add `--apply` after the person agrees. `--draft` only if the project chose draft-first.

## Writing Verified and Not covered

Verified says what ran, not what was intended: "pytest 102 passed; real audit against the project ran read-only". Not covered names every stand-in and every path not run, even when it makes the pull request look weaker. A pull request with an empty Not covered is not believed.
