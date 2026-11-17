# step-functions

One Step Functions state machine with what every one of ours needs (ADR-0009, wiki `Services/Step-Functions`):

- its **own role**, which only that state machine can assume, with exactly the permissions the caller passes in `policy_json`;
- a log group (`/aws/vendedlogs/states/<name>`) that receives errors and **never request data**;
- a **published version** on every change and an **alias** per name in `aliases`, so a release moves the alias and a rollback moves it back;
- alarms on executions that **fail, time out or are aborted**, telling `alarm_topic_arns`.

The definition is passed in already filled in (`definition`); the `provisioning` module fills the placeholders of the files in `mls-automation-api/statemachines`.

```hcl
module "provision" {
  source      = "../step-functions"
  name        = "mls-sfn-dev-lab-001"
  account_id  = "096631344162"
  definition  = local.definition
  policy_json = local.policy
  aliases     = ["dev"]
}
```

Tests: `terraform test` (mocked provider; nothing is created).
