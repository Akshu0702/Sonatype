# cloudwatch-alarms

Creates the SNS topics (one per severity) and CloudWatch metric alarms for the Sonatype Managed Lab.
Alerts, severities and routing are defined in the wiki: https://dev.azure.com/ML-AIML-COE/Sonatype-MLS/_wiki/wikis/Sonatype-MLS.wiki
(Operations / Alert Catalogue, Operations / Monitoring and Alerting).

**Status: skeleton. Not applied and not yet validated with Terraform.**

## Rules enforced by this module

- Alarms are keyed by their Alert Catalogue id (for example `INF-01`).
- Every alarm needs an `owner` and a `runbook_url`. The module rejects an alarm without them.
- Severity must be one of `p1`, `p2`, `p3`, `p4`; it selects the SNS topic.
- Tags come from the caller, plus `severity`, `owner` and `runbook` on each alarm.

## Example

```hcl
module "alarms" {
  source      = "../../modules/cloudwatch-alarms"
  name_prefix = "mls-sns-lab-mgmt"
  kms_key_id  = "alias/mls-lab-secrets"
  tags        = { project = "mls", environment = "lab", function = "mgmt", owner = "platform", managed-by = "terraform" }

  alarms = {
    "INF-07" = {
      description         = "RDS free storage below 20 percent"
      namespace           = "AWS/RDS"
      metric_name         = "FreeStorageSpace"
      statistic           = "Minimum"
      threshold           = 40000000000
      comparison_operator = "LessThanThreshold"
      dimensions          = { DBInstanceIdentifier = "mls-rds-lab-db-001" }
      severity            = "p2"
      owner               = "platform"
      runbook_url         = "https://dev.azure.com/ML-AIML-COE/Sonatype-MLS/_wiki/wikis/Sonatype-MLS.wiki/Operations/Alert-Catalogue"
    }
  }
}
```

The threshold above is illustrative; real thresholds come from the Alert Catalogue and are tuned after the first weeks of use.

## Not in this module yet

- Subscriptions for the router function (added with the router story).
- Log-pattern metric filters and Config rules (separate modules or later changes).
- Dashboards.
