mock_provider "aws" {}

# The Gate 1 segmentation: offices reach shared services only; Env-1 and Env-2 cannot reach each other;
# automation in shared services reaches both environments.
variables {
  name         = "mls-tgw-prod-mgmt-001"
  route_tables = ["onprem", "shared", "env1", "env2"]

  vpc_attachments = {
    shared = {
      vpc_id       = "vpc-0aaaaaaaaaaaaaaa1"
      subnet_ids   = ["subnet-0aaaaaaaaaaaaaaa1", "subnet-0aaaaaaaaaaaaaaa2", "subnet-0aaaaaaaaaaaaaaa3"]
      route_table  = "shared"
      propagate_to = ["onprem", "env1", "env2"]
    }
    env1 = {
      vpc_id       = "vpc-0aaaaaaaaaaaaaaa2"
      subnet_ids   = ["subnet-0bbbbbbbbbbbbbbb1", "subnet-0bbbbbbbbbbbbbbb2", "subnet-0bbbbbbbbbbbbbbb3"]
      route_table  = "env1"
      propagate_to = ["shared"]
    }
    env2 = {
      vpc_id       = "vpc-0aaaaaaaaaaaaaaa3"
      subnet_ids   = ["subnet-0ccccccccccccccc1", "subnet-0ccccccccccccccc2", "subnet-0ccccccccccccccc3"]
      route_table  = "env2"
      propagate_to = ["shared"]
    }
  }

  external_attachments = {
    vpn_sonatype = { attachment_id = "tgw-attach-0aaaaaaaaaaaaaaa1", route_table = "onprem", propagate_to = ["shared"] }
    vpn_motivity = { attachment_id = "tgw-attach-0aaaaaaaaaaaaaaa2", route_table = "onprem", propagate_to = ["shared"] }
  }
}

run "nothing_is_automatic" {
  command = plan

  assert {
    condition     = aws_ec2_transit_gateway.this.default_route_table_association == "disable" && aws_ec2_transit_gateway.this.default_route_table_propagation == "disable" && aws_ec2_transit_gateway.this.auto_accept_shared_attachments == "disable"
    error_message = "Default association, default propagation and auto-accept must all be off."
  }
}

run "one_route_table_per_domain_and_one_association_per_attachment" {
  command = plan

  assert {
    condition     = length(aws_ec2_transit_gateway_route_table.this) == 4 && length(aws_ec2_transit_gateway_route_table_association.this) == 5
    error_message = "Four route tables and five associations (three VPCs and two VPNs)."
  }
}

run "environments_cannot_reach_each_other" {
  command = plan

  assert {
    condition     = length(aws_ec2_transit_gateway_route_table_propagation.this) == 7
    error_message = "Seven propagations: 2 VPN to shared, shared to 3 tables, env1 and env2 to shared."
  }

  assert {
    condition = (
      !contains(keys(aws_ec2_transit_gateway_route_table_propagation.this), "env1->env2") &&
      !contains(keys(aws_ec2_transit_gateway_route_table_propagation.this), "env2->env1")
    )
    error_message = "Env-1 and Env-2 must not learn each other's ranges."
  }
}

run "offices_reach_shared_services_only" {
  command = plan

  assert {
    condition = (
      contains(keys(aws_ec2_transit_gateway_route_table_propagation.this), "vpn_sonatype->shared") &&
      contains(keys(aws_ec2_transit_gateway_route_table_propagation.this), "vpn_motivity->shared") &&
      !contains(keys(aws_ec2_transit_gateway_route_table_propagation.this), "vpn_sonatype->env1") &&
      !contains(keys(aws_ec2_transit_gateway_route_table_propagation.this), "vpn_sonatype->env2") &&
      !contains(keys(aws_ec2_transit_gateway_route_table_propagation.this), "env1->onprem")
    )
    error_message = "Offices may reach shared services only, and the environments must not be visible to the offices."
  }
}

run "automation_in_shared_services_reaches_both_environments" {
  command = plan

  assert {
    condition = (
      contains(keys(aws_ec2_transit_gateway_route_table_propagation.this), "shared->env1") &&
      contains(keys(aws_ec2_transit_gateway_route_table_propagation.this), "shared->env2") &&
      contains(keys(aws_ec2_transit_gateway_route_table_propagation.this), "env1->shared") &&
      contains(keys(aws_ec2_transit_gateway_route_table_propagation.this), "env2->shared")
    )
    error_message = "Shared services and each environment must see each other."
  }
}

run "static_default_route_and_blackhole" {
  command = plan

  variables {
    static_routes = {
      env1_default = { route_table = "env1", cidr = "0.0.0.0/0", attachment = "shared" }
      env_bogon    = { route_table = "env2", cidr = "10.41.0.0/20", blackhole = true }
    }
  }

  assert {
    condition     = length(aws_ec2_transit_gateway_route.static) == 2 && aws_ec2_transit_gateway_route.static["env_bogon"].blackhole == true
    error_message = "Both static routes are expected, one of them a blackhole."
  }
}

run "vpc_routes_point_at_the_gateway" {
  command = plan

  variables {
    vpc_routes = {
      shared_app_a_to_env1 = { route_table_id = "rtb-0aaaaaaaaaaaaaaa1", destination_cidr_block = "10.41.0.0/20" }
    }
  }

  assert {
    condition     = length(aws_route.vpc) == 1 && aws_route.vpc["shared_app_a_to_env1"].destination_cidr_block == "10.41.0.0/20"
    error_message = "One VPC route to the transit gateway is expected."
  }
}

run "rejects_an_unknown_route_table" {
  command = plan

  variables {
    vpc_attachments = {
      shared = {
        vpc_id      = "vpc-0aaaaaaaaaaaaaaa1"
        subnet_ids  = ["subnet-0aaaaaaaaaaaaaaa1"]
        route_table = "does-not-exist"
      }
    }
    external_attachments = {}
  }

  expect_failures = [var.vpc_attachments]
}

run "rejects_a_static_route_to_a_missing_attachment" {
  command = plan

  variables {
    static_routes = {
      bad = { route_table = "env1", cidr = "0.0.0.0/0", attachment = "nowhere" }
    }
  }

  expect_failures = [var.static_routes]
}

run "rejects_a_public_asn" {
  command = plan

  variables {
    amazon_side_asn = 7224
  }

  expect_failures = [var.amazon_side_asn]
}
