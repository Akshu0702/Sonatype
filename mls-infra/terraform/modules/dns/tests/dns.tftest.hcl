mock_provider "aws" {}

variables {
  zone_name = "lab.internal"
  vpc_ids   = ["vpc-0aaaaaaaaaaaaaaa1", "vpc-0aaaaaaaaaaaaaaa2", "vpc-0aaaaaaaaaaaaaaa3"]
}

run "zone_is_created_in_the_first_vpc_and_associated_with_the_rest" {
  command = plan

  assert {
    condition     = aws_route53_zone.private.name == "lab.internal" && length(aws_route53_zone_association.extra) == 2
    error_message = "One zone, and two further VPC associations."
  }
}

run "no_resolver_endpoint_by_default" {
  command = plan

  assert {
    condition     = length(aws_route53_resolver_endpoint.inbound) == 0 && length(aws_security_group.resolver) == 0
    error_message = "No resolver endpoint unless asked for."
  }
}

run "inbound_resolver_for_the_offices" {
  command = plan

  variables {
    resolver_inbound = {
      name          = "mls-sg-prod-mgmt-001"
      vpc_id        = "vpc-0aaaaaaaaaaaaaaa1"
      subnet_ids    = ["subnet-0aaaaaaaaaaaaaaa1", "subnet-0aaaaaaaaaaaaaaa2"]
      allowed_cidrs = ["192.0.2.0/24", "198.51.100.0/24"]
    }
  }

  assert {
    condition     = length(aws_route53_resolver_endpoint.inbound) == 1 && aws_route53_resolver_endpoint.inbound[0].direction == "INBOUND"
    error_message = "One inbound resolver endpoint is expected."
  }

  assert {
    condition     = length(aws_vpc_security_group_ingress_rule.resolver) == 4
    error_message = "Two office ranges, each allowed on TCP and UDP port 53, is four rules."
  }
}

run "records_are_created" {
  command = plan

  variables {
    records = {
      artifactory_env1 = { name = "artifactory.env1.lab.internal", type = "A", records = ["10.41.0.10"] }
    }
  }

  assert {
    condition     = aws_route53_record.this["artifactory_env1"].ttl == 300
    error_message = "Records default to a 300 second TTL."
  }
}

run "rejects_a_single_subnet_endpoint" {
  command = plan

  variables {
    resolver_inbound = {
      name          = "x"
      vpc_id        = "vpc-0aaaaaaaaaaaaaaa1"
      subnet_ids    = ["subnet-0aaaaaaaaaaaaaaa1"]
      allowed_cidrs = ["192.0.2.0/24"]
    }
  }

  expect_failures = [var.resolver_inbound]
}

run "rejects_a_one_label_zone" {
  command = plan

  variables {
    zone_name = "internal"
  }

  expect_failures = [var.zone_name]
}
