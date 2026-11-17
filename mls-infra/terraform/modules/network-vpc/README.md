# network-vpc

A VPC with subnet tiers across two or three availability zones, per-subnet route tables, optional NAT gateways, an S3 gateway endpoint and optional flow logs.
Design: https://dev.azure.com/ML-AIML-COE/Sonatype-MLS/_wiki/wikis/Sonatype-MLS.wiki/Infrastructure/Terraform-Modules and https://dev.azure.com/ML-AIML-COE/Sonatype-MLS/_wiki/wikis/Sonatype-MLS.wiki/Architecture/Infrastructure-Diagrams

**Status: validated with Terraform 1.9.8 (`fmt`, `validate`, `test` with a mock provider). Not applied to AWS. Needs Terraform 1.9 or later.**

## What it does

- Creates the VPC (DNS on) and **empties the default security group** so nothing is open by accident.
- Creates one subnet and one route table per tier per availability zone. Ranges come from explicit `newbits` and `netnums`, so they never overlap by accident.
- A tier marked `public` routes to an internet gateway; it is intended only for NAT gateways. With `enable_nat`, tiers listed in `nat_routed_tiers` route to the NAT gateway of their own zone.
- Adds an S3 gateway endpoint to the private route tables (on by default) and VPC flow logs to S3 when given a bucket.
- Outputs subnet and route table ids by tier, in the order of `azs`, so a Transit Gateway module can add routes.

## Example: shared services VPC (10.40.0.0/20)

```hcl
module "vpc" {
  source     = "../../modules/network-vpc"
  name       = module.name_vpc.name
  cidr_block = "10.40.0.0/20"
  azs        = ["ap-south-1a", "ap-south-1b", "ap-south-1c"]
  tags       = module.name_vpc.tags

  tiers = {
    app    = { newbits = 3, netnums = [0, 1, 2] }              # /23
    data   = { newbits = 5, netnums = [16, 17, 18] }           # /25
    tgw    = { newbits = 8, netnums = [192, 193, 194] }        # /28
    public = { newbits = 7, netnums = [104, 105, 106], public = true } # /27, NAT only
  }

  enable_nat       = true
  nat_routed_tiers = ["app", "data"]
}
```

Lab VPCs (10.41.0.0/20, 10.42.0.0/20) use `nodes` (/22), `data` (/25) and `tgw` (/28) with no public tier and no NAT; their outbound traffic goes through the Transit Gateway.

## Not in this module yet

- Transit Gateway attachments, segmented routing and VPN: separate module (task remains open).
- Interface endpoints (ECR, STS, Secrets Manager, CloudWatch Logs): separate module.
- Network ACLs.

## Test

```
terraform init -backend=false
terraform test
```

## Open points

- Subnet and route table `Name` tags end with the tier and the zone letter instead of the standard's three-digit id. To be recorded in the naming standard.
