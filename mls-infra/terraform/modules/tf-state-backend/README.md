# tf-state-backend

Creates the S3 bucket and DynamoDB lock table that hold Terraform state for one account.
Design: https://dev.azure.com/ML-AIML-COE/Sonatype-MLS/_wiki/wikis/Sonatype-MLS.wiki/Infrastructure/Terraform-Modules

**Status: validated with Terraform 1.9.8 (`fmt`, `validate`, `test` with a mock provider). Not applied to AWS.**

## What it creates

- An S3 bucket with versioning, all four public access blocks, ACLs disabled, a TLS-only policy, encryption (KMS key if given, otherwise AWS-managed), lifecycle rules for old versions and optional access logging.
- A DynamoDB lock table (pay per request, encrypted, point-in-time recovery) unless `create_lock_table = false`.
- `prevent_destroy` on the bucket and the table.

## Bootstrap (once per account)

1. Apply this module with a local backend. `kms_key_arn` may be null at this point.
2. Write a `backend.hcl` from the `backend_config` output, with a `key` for the root module, and run `terraform init -migrate-state -backend-config=backend.hcl`.
3. Once the KMS module is applied, set `kms_key_arn` and apply again.

## Locking choice (open)

The lock table works with every Terraform version. Terraform 1.10 and later can lock with an S3 lock file instead; when the Terraform version is pinned for the repository, decide whether to set `create_lock_table = false`. See Known Issue 17 in the wiki.

## Test

```
terraform init -backend=false
terraform test
```
