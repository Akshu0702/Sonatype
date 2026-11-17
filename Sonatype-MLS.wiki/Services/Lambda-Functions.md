# Lambda Functions

The workers the state machines call. Each function has its own IAM role scoped to one bucket or cluster, and credentials come from Secrets Manager.

[[_TOC_]]

**Status legend.** **Built** = running and used. **Partly** = exists, with a stated gap. **Not built** = designed, nothing runs. A plan is never described as behaviour.

| Function | What it does | Stage | Status |
|---|---|---|---|
| `fn-artifact-upload` | Presigned upload of build ZIPs to S3, checksum and inventory | S1 to S3 | Partly |
| `fn-env-status` | Node and pod health, rollout state, Artifactory health | Status | Partly |
| `fn-deployed-versions` | Artifactory and Firewall plugin versions (`firewallVersion`) | S6 | Partly |
| `fn-plugin-deploy` | Under [ADR-0002](/Decisions/ADR-0002-Immutable-Plugin-Bundle) this no longer copies files into pods. It records the requested bundle version and requests the Argo CD sync | S4 to S5 | Not built |
| `fn-rollout` | Approval-gated restart, rollout watch, rollback | S5 | Partly |
| `fn-evidence` | Stores logs, API responses and screenshots in S3 | S7 to S9 | Partly |
| `fn-provision-env` | Starts the provisioning module for a request | Provisioning | Not built |
| `fn-ttl-reaper` | Finds and removes expired environments | Provisioning | Not built |
| `fn-preflight` | Snapshot, dry-run diff and tag checks before an upgrade | Upgrade | Not built |
| `fn-postflight` | Pod, release and version checks after an upgrade | Upgrade | Not built |

## Conventions

- Code is in `lambdas/<function>/` in `mls-automation-api`, with its own tests and README.
- Functions are packaged by the `lambda-function` Terraform module and deployed from a versioned artifact, never from a laptop.
- Logs go to CloudWatch and are shipped to the log platform; secrets are masked.
- A function that changes state is idempotent, so a retry by the state machine is safe.
