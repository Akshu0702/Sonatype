# Deploying with the pipeline

Design: https://dev.azure.com/ML-AIML-COE/Sonatype-MLS/_wiki/wikis/Sonatype-MLS.wiki/Engineering/Branching-and-Release

**Status: not yet run. The YAML compiles in Azure DevOps and the plan guard has unit tests. No deployment has been made.**

## What runs

| Pipeline | Does |
|---|---|
| `deploy-mls-infra.yml` | Self-test of the guard, preflight (account, region, identity), `terraform plan`, plan guard, **approval**, then `terraform apply` of the same plan file |
| `deploy-destroy-mls-infra.yml` | The same with `plan -destroy`; only deletes are allowed; **approval** before the destroy |

Both are manual (no trigger). The apply and destroy stages run only from `develop`, `main` or `release/*`.

## Guards, in the order they act

1. **Identity.** `deploy-preflight.sh` stops unless the credentials are for account `096631344162`, region `ap-south-1`, and the `mls-deploy` identity.
2. **Plan guard** (`scripts/ci/plan-guard.py`). A plan is refused if any resource would be updated, deleted or replaced (only `create` is allowed unless `allowedActions` is changed on purpose), if it imports an existing resource, or if any AWS provider is not pinned to `ap-south-1`.
3. **Approval.** The `mls-lab-apply` and `mls-lab-destroy` environments hold an approval check. Nothing after it runs until the named approver approves.
4. **Same plan.** The plan file is published as an artifact with its checksum; the apply stage verifies the checksum and applies that file, not a fresh plan.
5. **IAM.** The `mls-deploy` user's policies (`mls-deploy-allow`, `mls-deploy-guardrails`) limit it to Mumbai and `mls-` names, and deny changes to the default VPC. This is the last line if everything above fails.

## One-time setup

1. Create the IAM user `mls-deploy` with the two policies; create an access key.
2. In Azure DevOps: Pipelines, Library, **Variable group** `mls-aws-deploy`. Add `AWS_ACCESS_KEY_ID` and `AWS_SECRET_ACCESS_KEY`; click the **lock** on both so they are secret. Under Pipeline permissions, allow only these two pipelines.
3. Create the environments `mls-lab-apply` and `mls-lab-destroy` with an **Approvals** check naming the approver.
4. Register the two YAML files as pipelines. Needs an agent with Docker: set `agentPool` to a self-hosted pool if hosted minutes are not available.

## Not covered

- There is no `terraform/live/lab-test` composition yet; the pipeline needs one, with an S3 backend block pointing at **our own** state bucket, never an existing one.
- The state bucket must be created first with local state (a one-time, manual bootstrap).
- The plan artifact can contain values marked sensitive. Restrict who can open pipeline artifacts.
- Anyone who can edit the pipeline YAML on a protected branch and run it can use the secret variables. Keep edit rights tight.
- The guard checks what happens to existing resources and the region; it does not judge what is created. Review the plan.
