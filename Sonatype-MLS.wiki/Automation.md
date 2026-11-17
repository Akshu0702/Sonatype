# Automation

What is automated, how, and its status.

[[_TOC_]]

**Status legend.** **Built** = running and used. **Partly** = exists, with a stated gap. **Not built** = designed, nothing runs. A plan is never described as behaviour.

| Area | Status |
|---|---|
| Plugin deployment as an immutable bundle | Not built |
| Ticket lifecycle S1-S9 on Step Functions | Not built |
| Operational safeguards (runbook rules as controls) | Not built |
| On-demand provisioning with TTL | Not built |
| Firewall test harness | Not built |

## Plugin deployment

**Starting point (manual, Partly):** files are copied into each Artifactory pod with `kubectl cp`, then the StatefulSet is restarted. Pods can end up on different builds, and a mismatched Tomcat listener jar can stop a pod from starting.

**Design:**
1. Upload the bundle (groovy, plugin jar, listener jar, SHA-256 manifest) to S3.
2. Record approval in the portal.
3. Bump `plugin.version` in Git; the Helm values change drives the rollout.
4. An init container pulls and verifies the bundle, then places files in `plugins/` and the Tomcat lib directory.
5. Rolling restart, one pod at a time.
6. Check `firewallVersion` on every pod; a failure reverts `plugin.version`.

## Ticket lifecycle

Step Functions orchestrates S1 to S9 (intake, workspace, deploy, verify and restart, version check, test, blockers, report) with an approval gate before deploy. Lambda functions and Kubernetes Jobs are the workers. Execution history is audit evidence.

## Operational safeguards

| Rule | Control |
|---|---|
| Snapshot the database before an upgrade | Pre-flight step; upgrade blocked until the snapshot is available |
| Keep values and helm history | Values in Git; history archived to S3 |
| Dry run and diff first | Mandatory gate; the diff is attached to the ticket |
| Matching init-container and frontend tags | Derived from one version variable; registry check in pre-flight |
| No `--atomic`; never cancel a running upgrade | Workflow runs to completion or timeout |
| Watch for SafeListener errors | Log alarm opens a ticket and rolls back to the validated bundle |
| Fix a stuck release by patching its secret from a file | Approval-gated action |
| Same build on every pod | One bundle; checksum compared before tests |

## Provisioning

Request through the portal, API or CLI; Step Functions runs a Terraform module; environments use hardened Linux images with PostgreSQL pinned per configuration. TTL, owner and ticket tags are mandatory; default 8 hours, maximum 72 hours; a reaper removes expired environments. Images come from EC2 Image Builder, scanned before promotion.

## Test harness

A YAML matrix lists repository, format, mode and expected result. A Kubernetes Job runs each case, asserts the outcome (HTTP result, quarantine state, IQ report) and stores evidence in S3, linked in the portal. Cases: five repositories, five formats and the firewall modes, plus pipeline-stop tests for three CI/CD platforms.
