mock_provider "aws" {}

# Shared services VPC from the Gate 1 network plan: app /23, data /25, TGW /28, public /27 (NAT only).
variables {
  name       = "mls-vpc-prod-mgmt-001"
  cidr_block = "10.40.0.0/20"
  azs        = ["ap-south-1a", "ap-south-1b", "ap-south-1c"]
  tiers = {
    app    = { newbits = 3, netnums = [0, 1, 2] }
    data   = { newbits = 5, netnums = [16, 17, 18] }
    tgw    = { newbits = 8, netnums = [192, 193, 194] }
    public = { newbits = 7, netnums = [104, 105, 106], public = true }
  }
  enable_nat       = true
  nat_routed_tiers = ["app", "data"]
}

run "shared_services_layout" {
  command = plan

  assert {
    condition     = length(aws_subnet.this) == 12
    error_message = "Four tiers across three zones is twelve subnets."
  }

  assert {
    condition = (
      aws_subnet.this["app/ap-south-1a"].cidr_block == "10.40.0.0/23" &&
      aws_subnet.this["app/ap-south-1b"].cidr_block == "10.40.2.0/23" &&
      aws_subnet.this["app/ap-south-1c"].cidr_block == "10.40.4.0/23" &&
      aws_subnet.this["data/ap-south-1a"].cidr_block == "10.40.8.0/25" &&
      aws_subnet.this["tgw/ap-south-1a"].cidr_block == "10.40.12.0/28" &&
      aws_subnet.this["public/ap-south-1a"].cidr_block == "10.40.13.0/27"
    )
    error_message = "Subnet ranges do not match the network plan."
  }
}

run "one_nat_per_zone_and_routes_for_routed_tiers" {
  command = plan

  assert {
    condition     = length(aws_nat_gateway.this) == 3 && length(aws_eip.nat) == 3
    error_message = "One NAT gateway and address per zone."
  }

  assert {
    condition     = length(aws_route.nat_default) == 6 && length(aws_route.public_default) == 3
    error_message = "App and data tiers route to NAT in every zone (6); the public tier routes to the internet gateway (3)."
  }
}

run "no_public_tier_means_no_internet_gateway_or_nat" {
  command = plan

  variables {
    cidr_block = "10.41.0.0/20"
    tiers = {
      nodes = { newbits = 2, netnums = [0, 1, 2] }
      data  = { newbits = 5, netnums = [24, 25, 26] }
      tgw   = { newbits = 8, netnums = [208, 209, 210] }
    }
    enable_nat       = false
    nat_routed_tiers = []
  }

  assert {
    condition     = length(aws_subnet.this) == 9 && length(aws_internet_gateway.this) == 0 && length(aws_nat_gateway.this) == 0
    error_message = "A VPC without a public tier has no internet gateway and no NAT."
  }

  assert {
    condition     = aws_subnet.this["nodes/ap-south-1a"].cidr_block == "10.41.0.0/22" && aws_subnet.this["data/ap-south-1a"].cidr_block == "10.41.12.0/25" && aws_subnet.this["tgw/ap-south-1a"].cidr_block == "10.41.13.0/28"
    error_message = "Lab VPC ranges do not match the network plan."
  }
}

run "s3_endpoint_and_flow_logs_are_optional" {
  command = plan

  variables {
    enable_s3_gateway_endpoint = false
    flow_logs_s3_arn           = "arn:aws:s3:::mls-s3-prod-store-002"
  }

  assert {
    condition     = length(aws_vpc_endpoint.s3) == 0 && length(aws_flow_log.this) == 1
    error_message = "The S3 endpoint is off and one flow log is on."
  }
}

run "default_security_group_is_managed_with_no_rules" {
  command = plan

  assert {
    condition     = aws_default_security_group.this.tags["Name"] == "mls-vpc-prod-mgmt-001-default"
    error_message = "The default security group is managed by this module. It declares no rules, so Terraform removes all of them."
  }
}

run "rejects_wrong_number_of_netnums" {
  command = plan

  variables {
    tiers = {
      app = { newbits = 3, netnums = [0, 1] }
    }
    enable_nat = false
  }

  expect_failures = [var.tiers]
}

run "rejects_nat_without_a_public_tier" {
  command = plan

  variables {
    tiers = {
      app = { newbits = 3, netnums = [0, 1, 2] }
    }
  }

  expect_failures = [var.enable_nat]
}
