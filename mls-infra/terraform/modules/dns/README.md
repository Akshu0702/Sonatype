# dns

A Route 53 **private hosted zone** for lab names, associated with the lab VPCs, and an optional **inbound resolver endpoint** so office networks can resolve those names over the VPN.
Design: https://dev.azure.com/ML-AIML-COE/Sonatype-MLS/_wiki/wikis/Sonatype-MLS.wiki/Architecture/Infrastructure-Diagrams

**Status: validated with Terraform 1.9.8 (`fmt`, `validate`, `test` with a mock provider). Not applied to AWS.**

## Use

```hcl
module "dns" {
  source    = "../../modules/dns"
  zone_name = "lab.internal"
  vpc_ids   = [module.vpc_shared.vpc_id, module.vpc_env1.vpc_id, module.vpc_env2.vpc_id]
  tags      = module.name_dns.tags

  resolver_inbound = {
    name          = module.name_sg_dns.name
    vpc_id        = module.vpc_shared.vpc_id
    subnet_ids    = slice(module.vpc_shared.subnet_ids["app"], 0, 2)
    allowed_cidrs = ["<Sonatype office range>", "<Motivity office range>"]
  }
}
```

Give `resolver_ip_addresses` to the offices' DNS administrators so they forward the lab zone to them.

## Not in this module

- Failover records for the DR region (Route 53 health checks cannot reach private endpoints directly; this needs a CloudWatch-alarm-based health check and is a separate change).
- Outbound resolver rules (resolving office names from the lab).

## Open points

- The zone name `lab.internal` is an example; the real name is to be agreed.

## Test

```
terraform init -backend=false
terraform test
```
