# lab-test: on-demand environments

A request starts a Step Functions execution that launches **one EC2 instance** from the hardened image, with the mandatory tags (`owner`, `ticket`, `ttl`, `expires-at`, `project=mls`, `environment`, `managed-by=provisioning`), waits until it expires and terminates it. A second state machine, the **ttl-reaper**, runs every 15 minutes as a safety net. An image pipeline builds the hardened image. Design: wiki `Provisioning-API`, `Automation`, ADR-0003, ADR-0004, ADR-0009.

**Status: written and tested, NOT applied.** `terraform plan` against the real account with read-only credentials computes **34 resources to add, 0 to change, 0 to destroy** and then stops, because the read-only user may not call `states:ValidateStateMachineDefinition`, which the AWS provider calls during plan. Nothing was created, launched or changed in AWS.

| Piece | Where |
|---|---|
| The two state machine definitions | `mls-automation-api/statemachines/` (ADR-0009), read through `definitions_dir` |
| `provisioning` module | the flow: image lookup, catalogue, security group, instance profile, the two state machines, the reaper's timetable |
| `step-functions` module | one state machine: role, log group, published version, alias, alarms on failed, timed-out and aborted executions |
| `image-builder` module | EC2 Image Builder pipeline: Ubuntu 24.04 LTS, `ansible/playbooks/harden.yml`, Inspector scan |
| `iam-by-hand.md`, `mls-deploy-allow-additions.json` | what a person must add in IAM first |

## Safety rails

- The provider refuses any account other than `096631344162` and any region other than `ap-south-1`.
- A launch is refused unless it carries the tags above; only `t3.small`, `t3.medium` and `t3.large` (burstable, run with **standard credits**, so an environment can never cost more than its hourly price); only into the named subnets (never the default VPC); only encrypted volumes; no key pair; IMDSv2 only; no inbound rule unless one is named.
- Nothing may terminate an instance that is not tagged `project=mls` **and** `managed-by=provisioning`.
- An instance with no `expires-at` tag is never terminated by the reaper; the reaper's run **fails** instead, which raises the failed-execution alarm.
- The longest life is 72 hours (a longer one needs a new ticket); the default is 8 hours.

## Choose how environments reach Session Manager

The lab VPC has two public subnets that do not assign public addresses, no private subnet, no NAT and no endpoints. An instance with no public address there cannot reach Systems Manager, so it cannot be used. Pick one before the first environment:

| Option | What it takes | Cost |
|---|---|---|
| `associate_public_ip = true` | Nothing new. Outbound only; the security group allows nothing in. This is how the existing hosts work | $0.005 per hour per running environment (public IPv4 address) |
| `create_ssm_endpoints = true` plus private subnets | A private subnet tier in the network stack (a change to `hosts`) | About $22 to $30 a month for three interface endpoints in one subnet, running or not |
| NAT gateway | A change to the network stack | About $33 a month plus data |

The default is **off** (no public address). The first environment is useless until you pick.

## Cost of one environment (estimates)

Third-party price lists for Mumbai on-demand Linux put `t3.small` at about **$0.0224 an hour** and `t3.medium` at about **$0.0448** ([DoiT](https://www.doit.com/compute/spot/ap-south-1/t3.small), [aws-pricing.com](https://aws-pricing.com/t3.medium.html)); the official AWS price list could not be read with the read-only user, so check it before relying on the figures. A 30 GB gp3 volume is about $0.09 a GB-month, and a public IPv4 address $0.005 an hour.

| Environment | 8 hours (default) | 72 hours (maximum) |
|---|---|---|
| `t3.small`, 30 GB, no public address | about $0.21 | about $1.88 |
| `t3.small`, 30 GB, public address | about $0.25 | about $2.24 |
| `t3.medium`, 30 GB, public address | about $0.43 | about $3.85 |

**Why `t3.small` is the default:** the first configurations are an operating system plus PostgreSQL 16 to test against, which needs about 2 GiB of memory; a larger type costs twice as much for nothing. A configuration that needs more names `t3.medium` or `t3.large` (both allowed); a bigger family needs a change to `allowed_instance_types`, which a reviewer will see.

Standing cost with nothing running: the reaper (about 17,000 state transitions a month, under $1), six alarms (about $0.60), logs and an SNS topic (cents). The image pipeline costs a `t3.medium` for the length of a build (cents) plus about $0.05 a GB-month for the image's snapshot, and Inspector bills per scan.

## Before the first apply (in this order)

1. `iam-by-hand.md`, steps 1 to 3: a person updates `mls-deploy-allow`, and optionally activates Inspector.
2. Decide how environments reach Session Manager (table above) and pass `-var associate_public_ip=true` or the endpoint options.
3. Check out `mls-automation-api` next to this repository (the pipeline does this); `definitions_dir` points at its `statemachines/` folder.
4. The image: upload the archive (`iam-by-hand.md`, step 5), apply, start a build with the output `start_image_build_command`, then set `hardened_ami_name_prefix = "mls-hardened-ubuntu-24"` and apply again. **Until then environments use plain Ubuntu, which is not hardened** (the output `uses_fallback_image` says so). The image build also needs a subnet with outbound access, which the lab VPC does not have today.

```
cd terraform/live/lab-test/provisioning
terraform init -backend-config="bucket=mls-s3-dev-store-001" -backend-config="key=lab-test/provisioning.tfstate" \
  -backend-config="region=ap-south-1" -backend-config="dynamodb_table=mls-s3-dev-store-001-lock" -backend-config="encrypt=true"
terraform plan -out=tfplan -var vpc_id=<vpc> -var 'subnet_ids=["<subnet-a>","<subnet-b>"]'
terraform show -json tfplan > plan.json && python3 ../../../../scripts/ci/plan-guard.py plan.json --allow create --expect-region ap-south-1
# apply only after review and explicit approval
```

## The first environment (needs your go-ahead)

Start the alias in the output `provision_alias_arn` with `{"environmentId":"env-first01","configuration":"ubuntu24-pg16","owner":"platform","ticket":"MLS-1","ttlHours":1}`. It launches one `t3.small`, costs a few cents, and is terminated an hour later by its own execution (or by the reaper within 15 minutes after that). To end it sooner: set its `expires-at` tag to the current time and start the reaper alias.

## Not covered

- Nothing was applied or launched; the state machines have never run in AWS. The emulator tests in `mls-automation-api` prove the flow and the expressions; they cannot prove the service accepts the definition or that EC2 accepts the arguments. The first `terraform plan` with `states:ValidateStateMachineDefinition` allowed validates the first; the first environment proves the second.
- The hardened image has never been built. The Ansible baseline is tested in containers (`ansible/README.md`), not on a real host.
- The provisioning API (ticket to execution) is specified (`mls-automation-api/openapi/provisioning.v1.yaml`) but not implemented.
- Teardown evidence (`GET /environments/{id}/evidence`) and quotas are not built.
