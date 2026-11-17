# kms-keys

Creates customer-managed KMS keys with aliases, one per purpose (secrets, s3, rds, ebs, efs, logs).
Design: https://dev.azure.com/ML-AIML-COE/Sonatype-MLS/_wiki/wikis/Sonatype-MLS.wiki/Infrastructure/Terraform-Modules and https://dev.azure.com/ML-AIML-COE/Sonatype-MLS/_wiki/wikis/Sonatype-MLS.wiki/Security-and-Compliance

**Status: validated with Terraform 1.9.8 (`fmt`, `validate`, `test` with a mock provider). Not applied to AWS.**

## Rules

- Alias is `alias/<name_prefix>-<purpose>`, for example `alias/mls-dev-secrets`.
- Key rotation is on by default; the deletion window is 30 days by default (7 to 30 allowed).
- The generated policy lets the account administer the key through IAM, and adds optional **admins** (manage, cannot use) and **users** (encrypt and decrypt, cannot manage). A custom policy replaces it.
- Keys carry the caller's tags and a `purpose` tag.

## Use

```hcl
module "kms" {
  source      = "../../modules/kms-keys"
  name_prefix = "mls-dev"
  tags        = module.name_kms.tags

  keys = {
    secrets = { description = "Secrets Manager" }
    s3      = { description = "Object storage" }
    rds     = { description = "Databases" }
  }
}
```

## Test

```
terraform init -backend=false
terraform test
```

## Open points

- Roles for key admins and users are added when the identity module exists.
- Multi-region keys for the DR region are not included yet.
