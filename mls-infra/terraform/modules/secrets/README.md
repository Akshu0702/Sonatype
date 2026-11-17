# secrets

Creates AWS Secrets Manager secrets with the standard name, a customer-managed key, a recovery window and optional rotation, DR replica and resource policy.
Design: https://dev.azure.com/ML-AIML-COE/Sonatype-MLS/_wiki/wikis/Sonatype-MLS.wiki/Infrastructure/Terraform-Modules and https://dev.azure.com/ML-AIML-COE/Sonatype-MLS/_wiki/wikis/Sonatype-MLS.wiki/Security-and-Compliance

**Status: validated with Terraform 1.9.8 (`fmt`, `validate`, `test` with a mock provider). Not applied to AWS.**

## The rule that matters: no value ever enters Terraform

This module creates the **secret**, never a **version**. Values are set by the service that owns them, by RDS (for database credentials), or by an operator, so a secret value is never in code, in a variable or in Terraform state.

## Names

`<project>/<environment>/<service>/<purpose>`, for example `mls/prod/iam/db-password`. The map key is `<service>/<purpose>`.

## Use

```hcl
module "secrets" {
  source      = "../../modules/secrets"
  environment = "prod"
  tags        = module.name_secrets.tags

  secrets = {
    "iam/db-password" = { description = "Portal database credentials", kms_key_id = module.kms.key_arns["secrets"] }
    "keycloak/admin"  = { description = "Keycloak bootstrap administrator", kms_key_id = module.kms.key_arns["secrets"] }
  }
}
```

## Options per secret

Recovery window (7 to 30 days, default 30), rotation with a Lambda function, replicas in other regions (for the DR region) and a resource policy.

## Not in this module

- External Secrets for the Kubernetes clusters (part of the EKS add-ons).
- The rotation functions themselves.

## Test

```
terraform init -backend=false
terraform test
```
