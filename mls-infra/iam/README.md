# IAM policies for the deploy identity

The two policies attached to the `mls-deploy` IAM user and to the build agent's role. They are created and changed by hand in IAM (a new version of each, never edited in place by a pipeline).

| File | Purpose |
|---|---|
| `mls-deploy-allow-policy.json` | What the identity may do: Mumbai only, `mls-` names, instance profiles, schedules, no new people or keys |
| `mls-deploy-guardrails-policy.json` | What it may never do, whatever the first policy says: any other region, the default VPC, new people, keys or groups |

**Status: in use for the `mls-deploy` user. Verified on 14 Nov 2026 with read-only and dry-run calls: Mumbai reads work; us-east-1 reads and S3 object reads are refused; deleting a default-VPC subnet (dry run), creating an IAM user, attaching an admin policy and creating a role outside `mls-*` are all refused.** Not simulated with the IAM policy simulator.

The allow policy gained instance profiles and EventBridge Scheduler schedules in this change; after merging, publish it as a new policy version in IAM.
