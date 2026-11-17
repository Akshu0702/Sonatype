# ec2-host

One EC2 instance with its role, security group and an optional start and stop timetable. Built for the lab's build agent and application host.
Design: https://dev.azure.com/ML-AIML-COE/Sonatype-MLS/_wiki/wikis/Sonatype-MLS.wiki/Infrastructure/Terraform-Modules

**Status: validated with Terraform 1.9.8 (`fmt`, `validate`, `test` with a mock provider, 18 tests). Planned against the real account with read-only credentials; not applied.**

## Secure by construction

| Choice | Setting |
|---|---|
| Access | Session Manager only: no key pair, no SSH |
| Metadata | IMDSv2 required, instance tags not exposed, hop limit 1 (2 only for a build agent whose job containers must use its role) |
| Disk | Encrypted gp3 root volume; the AWS-managed EBS key unless a key is given |
| Inbound | None by default. A rule open to `0.0.0.0/0` is refused at plan time; each rule names exactly one source |
| Outbound | TCP 80 and 443 only (Azure DevOps, package repositories, registries and AWS endpoints are all HTTPS) |
| Image | Canonical Ubuntu 24.04 LTS, looked up once; a newer image or an edited boot script never replaces a running instance (`ignore_changes`) |
| Burstable types | `cpu_credits = "standard"` caps the cost of a t-series instance at its hourly price (it slows down at the baseline instead of billing extra) |
| Shutdown | Stopping from inside stops the instance, it is never terminated |
| Timetable | Optional start and stop through EventBridge Scheduler; its role may start and stop this one instance and nothing else |

## Use

```hcl
module "agent" {
  source    = "../../modules/ec2-host"
  name      = module.name_agent.name
  tags      = module.name_agent.tags
  vpc_id    = module.vpc.vpc_id
  subnet_id = module.vpc.subnet_ids["hosts"][0]

  instance_type       = "m6i.xlarge"
  associate_public_ip = true   # outbound only; there is no inbound rule
  imds_hop_limit      = 2
  managed_policy_arns = ["arn:aws:iam::<account>:policy/mls-deploy-allow"]
  schedule = {
    enabled    = true
    start_cron = "cron(30 10 ? * MON-FRI *)"
    stop_cron  = "cron(30 19 ? * MON-FRI *)"   # India time
  }
}
```

## Open points

- **Scheduled start and a customer-managed key.** A timetable that starts an instance whose disk uses a customer-managed key needs the timetable's role to be allowed to use that key, or the start fails quietly. The lab hosts use the AWS-managed EBS key for this reason. Using a customer-managed key means adding the key permissions to the timetable role first.
- The public address changes whenever the instance is stopped and started. Nothing depends on it.
- No Elastic IP, no extra volumes, no load balancer in this module.

## Test

```
terraform init -backend=false
terraform test
```
