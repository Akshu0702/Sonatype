# lab-test: build agent and application host

The first real environment, in account `096631344162`, region `ap-south-1` only.
Design: https://dev.azure.com/ML-AIML-COE/Sonatype-MLS/_wiki/wikis/Sonatype-MLS.wiki/Infrastructure/Terraform-Modules

**Status: planned against the real account with read-only credentials (`bootstrap`: 8 to add; `hosts`: 36 to add; 0 changed or destroyed; both accepted by the plan guard). Nothing has been applied.**

| Folder | Creates |
|---|---|
| `bootstrap/` | The state bucket `mls-s3-dev-store-001` and its lock table. Local state, once |
| `hosts/` | A small VPC (10.40.0.0/20, two public subnets, no NAT gateway), the build agent `mls-ec2-dev-mgmt-001` (t3.xlarge, burstable, standard credits so it never costs more than the hourly price) and the application host `mls-ec2-dev-lab-001` (m6i.2xlarge), each with a role, security group and a weekday timetable (start 10:30, stop 19:30 India time) |

## Safety rails

- Both providers refuse any account other than `096631344162` (`allowed_account_ids`) and any region other than `ap-south-1` (a variable validation, checked again by the plan guard).
- Every name starts with `mls-`; nothing in the account is imported or changed. The `mls-deploy` IAM policies deny everything outside Mumbai and the default VPC.
- The instances have **no inbound rule from the internet**, no key pair and no SSH. The public subnet is for outbound web traffic only.

## Before the first apply

1. **Update the IAM policy `mls-deploy-allow`** with the new version (`mls-deploy-allow-policy.json`): it adds instance profiles and the start/stop schedules, which the first version did not allow.
2. Create the agent pool `mls-agents` in Azure DevOps (Organization settings, Agent pools).

## Apply, in order (from a workstation, under approval)

```
# 1. state bucket
cd terraform/live/lab-test/bootstrap
terraform init && terraform plan -out=tfplan
terraform show -json tfplan > plan.json && python3 ../../../../scripts/ci/plan-guard.py plan.json --allow create --expect-region ap-south-1
terraform apply tfplan

# 2. hosts (state in the new bucket)
cd ../hosts
terraform init -backend-config="bucket=mls-s3-dev-store-001" -backend-config="key=lab-test/hosts.tfstate" \
  -backend-config="region=ap-south-1" -backend-config="dynamodb_table=mls-s3-dev-store-001-lock" -backend-config="encrypt=true"
terraform plan -out=tfplan
terraform show -json tfplan > plan.json && python3 ../../../../scripts/ci/plan-guard.py plan.json --allow create --expect-region ap-south-1
terraform apply tfplan
```

## Register the build agent (once)

The boot script installs the tools and the Azure Pipelines agent **software**; registration needs a token that must never be in a script. Create a personal access token in Azure DevOps with the scope **Agent Pools (Read and manage)** and a short life, then:

```
aws ssm start-session --target <agent instance id> --region ap-south-1 --profile mls-deploy
sudo -u azagent bash
cd /opt/azagent
./config.sh --unattended --url https://dev.azure.com/ML-AIML-COE --auth pat --token <TOKEN> \
  --pool mls-agents --agent mls-agent-01 --acceptTeeEula
exit
sudo /opt/azagent/svc.sh install azagent && sudo /opt/azagent/svc.sh start
```

Revoke the token afterwards. The agent then appears as online in the pool, and the pipelines' `agentPool` parameter can be set to `mls-agents`.

## Not covered

- **Nothing is applied.** The plans were made with read-only credentials against the real account; the apply, the first boot and the Session Manager access are untested.
- The deploy pipelines still expect access keys in a variable group; they do not yet use the agent's instance role (a follow-up change).
- The boot scripts install packages at first boot and were not run; they are checked by reading, not by execution.
- The lock files were generated for Linux; add other platforms with `terraform providers lock` before using them on a workstation of another kind.
- No application is deployed to the application host; that is the next step.
