# State machines

Amazon States Language files, one per workflow (ADR-0009, wiki `Services/Step-Functions`). They are **templates**: the names in dollar-brace placeholders (`configurations_json`, `subnet_ids_json`, `security_group_id`, `instance_profile_name`, `associate_public_ip`, `environment`, `default_ttl_hours`, `max_ttl_hours`) are filled in by the Terraform module `provisioning` in `mls-infra` before deployment, so a file is valid JSON as it stands and a placeholder nobody fills makes the plan fail.

| File | Does | Status |
|---|---|---|
| `provision-environment.asl.json` | Validates a request, launches **one** EC2 instance from the hardened image with the mandatory tags, waits until it expires (re-reading the `expires-at` tag, so an extension is honoured), then terminates it | Written and emulator-tested; **never run in AWS** |
| `ttl-reaper.asl.json` | Every 15 minutes: terminates instances tagged `project=mls` and `managed-by=provisioning` whose `expires-at` has passed; fails (so the alarm fires) when a managed instance has no `expires-at` | Written and emulator-tested; **never run in AWS** |

Both use `QueryLanguage: JSONata` and direct SDK integrations (`arn:aws:states:::aws-sdk:ec2:...`): no Lambda function is needed.

## Input of `provision-environment`

```json
{ "environmentId": "env-3fa91c07", "configuration": "ubuntu24-pg16", "owner": "qa-team", "ticket": "MLS-101", "ttlHours": 8 }
```

`ttlHours` is optional (default 8, whole hours from 1 to 72). A request that breaks a rule ends as `InvalidRequest` or `UnknownConfiguration` before anything is created.

## Tests

```
bash scripts/ci/statemachine-check.sh      # valid JSON, StartAt exists
bash scripts/ci/statemachine-test.sh       # 30 cases in a local emulator (Docker, Node 22)
bash scripts/ci/statemachine-validate-aws.sh   # the service validates the definitions (needs states:ValidateStateMachineDefinition)
```

`statemachines/tests/emulator.mjs` runs the real files with the real JSONata library and answers every Task with a mock from `cases.json` (the answer, or an error). It proves the expressions and the flow: what is rejected, which tags and settings a launch carries, that a failed launch is retried and never duplicated (the client token is the environment id), that an extended expiry makes the wait start again, that the reaper reads every page, never terminates an untagged instance, and terminates at most 100 per run. **It is not the AWS service.** Amazon's own local emulator image predates JSONata and cannot run these files. Two things only AWS can say: that the service accepts the definitions (`statemachine-validate-aws.sh`) and that EC2 accepts the arguments (the first real execution).
