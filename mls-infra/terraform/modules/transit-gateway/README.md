# transit-gateway

A transit gateway with **segmented routing**: nothing is associated or propagated by default, and every path is declared.
Design: https://dev.azure.com/ML-AIML-COE/Sonatype-MLS/_wiki/wikis/Sonatype-MLS.wiki/Infrastructure/Terraform-Modules and https://dev.azure.com/ML-AIML-COE/Sonatype-MLS/_wiki/wikis/Sonatype-MLS.wiki/Architecture/Infrastructure-Diagrams

**Status: validated with Terraform 1.9.8 (`fmt`, `validate`, `test` with a mock provider). Not applied to AWS. Needs Terraform 1.9 or later.**

## How the routing works

- Each routing domain has a **route table** (for example `onprem`, `shared`, `env1`, `env2`).
- An attachment is **associated** with one table: traffic coming from it is looked up there.
- An attachment **propagates** its range into the tables listed in `propagate_to`. That decides who can reach it.

The Gate 1 rules, as asserted in the tests:

| Rule | How |
|---|---|
| Offices reach shared services only | VPN attachments are associated with `onprem` and propagate to `shared` only |
| Env-1 and Env-2 cannot reach each other | `env1` and `env2` propagate to `shared` only |
| Automation in shared services reaches both | `shared` propagates to `onprem`, `env1` and `env2` |

## Example

```hcl
module "tgw" {
  source       = "../../modules/transit-gateway"
  name         = module.name_tgw.name
  tags         = module.name_tgw.tags
  route_tables = ["onprem", "shared", "env1", "env2"]

  vpc_attachments = {
    shared = { vpc_id = module.vpc_shared.vpc_id, subnet_ids = module.vpc_shared.subnet_ids["tgw"], route_table = "shared", propagate_to = ["onprem", "env1", "env2"] }
    env1   = { vpc_id = module.vpc_env1.vpc_id,   subnet_ids = module.vpc_env1.subnet_ids["tgw"],   route_table = "env1",   propagate_to = ["shared"] }
    env2   = { vpc_id = module.vpc_env2.vpc_id,   subnet_ids = module.vpc_env2.subnet_ids["tgw"],   route_table = "env2",   propagate_to = ["shared"] }
  }

  external_attachments = {
    vpn_sonatype = { attachment_id = module.vpn.transit_gateway_attachment_ids["sonatype"], route_table = "onprem", propagate_to = ["shared"] }
    vpn_motivity = { attachment_id = module.vpn.transit_gateway_attachment_ids["motivity"], route_table = "onprem", propagate_to = ["shared"] }
  }
}
```

`vpc_routes` adds the routes in the VPC route tables that send other ranges to the gateway.

## Not in this module

- Sharing the gateway across accounts (AWS RAM).
- Network Firewall inspection and central egress; use `static_routes` to send a default route to the shared services attachment.
- Cross-region peering to the DR region.

## Test

```
terraform init -backend=false
terraform test
```
