# rds-postgres

A Multi-AZ RDS PostgreSQL instance with a private subnet group, a locked-down security group, an encrypted-connections parameter group, and a master password that RDS creates and keeps in Secrets Manager.
Design: https://dev.azure.com/ML-AIML-COE/Sonatype-MLS/_wiki/wikis/Sonatype-MLS.wiki/Architecture/Bill-of-Materials and https://dev.azure.com/ML-AIML-COE/Sonatype-MLS/_wiki/wikis/Sonatype-MLS.wiki/Security-and-Compliance/Hardening-Baselines

**Status: validated with Terraform 1.9.8 (`fmt`, `validate`, `test` with a mock provider). Not applied to AWS.**

## Defaults

| Setting | Default |
|---|---|
| Instance class | `db.m6g.large` (Gate 1 BOM) |
| Storage | 100 GiB gp3, encrypted with the customer key |
| Multi-AZ | on |
| Backups | 30 days; final snapshot on delete |
| Deletion protection | on |
| Public access | never |
| Connections | encrypted only (`rds.force_ssl`), connections and disconnections logged |
| Logs | PostgreSQL and upgrade logs exported to CloudWatch |
| Master password | created by RDS in Secrets Manager, encrypted with the key; **never in Terraform** |

## Use

```hcl
module "rds_portal" {
  source                 = "../../modules/rds-postgres"
  identifier             = module.name_rds_portal.name
  tags                   = module.name_rds_portal.tags
  engine_version         = "<decided version>"
  parameter_group_family = "<matching family>"
  kms_key_id             = module.kms.key_arns["rds"]
  vpc_id                 = module.vpc_shared.vpc_id
  subnet_ids             = module.vpc_shared.subnet_ids["data"]

  allowed_security_group_ids = [module.portal.security_group_id]
}
```

## Open points

- **`engine_version` has no default on purpose.** The PostgreSQL version is a decision (wiki Known Issues, item 6).
- The IQ Server database is a separate instance with its own call of this module (ADR: dedicated database).
- The cross-region read replica for DR is a separate change.

## Test

```
terraform init -backend=false
terraform test
```
