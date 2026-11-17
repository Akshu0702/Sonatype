# s3-bucket

A secure general-purpose S3 bucket for artifacts, evidence, filestores, backups and logs.
Design: https://dev.azure.com/ML-AIML-COE/Sonatype-MLS/_wiki/wikis/Sonatype-MLS.wiki/Architecture/Bill-of-Materials and https://dev.azure.com/ML-AIML-COE/Sonatype-MLS/_wiki/wikis/Sonatype-MLS.wiki/Architecture/Audit-and-Logging

**Status: validated with Terraform 1.9.8 (`fmt`, `validate`, `test` with a mock provider). Not applied to AWS. Needs Terraform 1.9 or later.**

## Secure by default

- All four public access blocks on, ACLs disabled, a TLS-only policy, versioning on, `force_destroy` off.
- Encryption with a customer-managed KMS key when `kms_key_arn` is given; otherwise AWS-managed (AES256), for buckets that a service writes to and cannot use a key.
- A built-in lifecycle rule that cleans up incomplete multipart uploads, plus the caller's rules.

## Options

| Option | Use |
|---|---|
| `lifecycle_rules` | Expire objects, move to infrequent access or Glacier, trim old versions |
| `replication` | Cross-region replication to the DR bucket (the replication role is created elsewhere) |
| `object_lock` | Retention for data that must not change, such as exported audit records. Must be chosen when the bucket is created |
| `access_log_bucket` | Server access logs |
| `extra_policy_json` | Extra statements, merged with the TLS-only statement, for example for VPC flow logs |

## Use

```hcl
module "evidence_bucket" {
  source      = "../../modules/s3-bucket"
  bucket_name = module.name_s3_evidence.name
  kms_key_arn = module.kms.key_arns["s3"]
  tags        = module.name_s3_evidence.tags

  lifecycle_rules = [{ id = "expire-old-versions", noncurrent_expiration_days = 90 }]
}
```

## Not in this module

- The replication IAM role.
- Bucket-specific service policies (for example for load balancer logs); pass them in `extra_policy_json`.
- Event notifications.

## Test

```
terraform init -backend=false
terraform test
```
