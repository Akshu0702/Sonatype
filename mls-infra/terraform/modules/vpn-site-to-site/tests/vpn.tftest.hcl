mock_provider "aws" {}

variables {
  name               = "mls-vpn-prod-mgmt-001"
  transit_gateway_id = "tgw-0aaaaaaaaaaaaaaa1"
  connections = {
    sonatype = { customer_gateway_ip = "203.0.113.10", bgp_asn = 65010 }
    motivity = { customer_gateway_ip = "198.51.100.20", bgp_asn = 65020, tunnel_inside_cidrs = ["169.254.10.0/30", "169.254.10.4/30"] }
  }
}

run "one_customer_gateway_and_connection_per_site" {
  command = plan

  assert {
    condition     = length(aws_customer_gateway.this) == 2 && length(aws_vpn_connection.this) == 2
    error_message = "One customer gateway and one connection per site."
  }

  assert {
    condition     = aws_customer_gateway.this["sonatype"].bgp_asn == "65010" && aws_customer_gateway.this["sonatype"].ip_address == "203.0.113.10"
    error_message = "The customer gateway carries the site's BGP ASN (as a string) and address."
  }
}

run "bgp_with_transit_gateway" {
  command = plan

  assert {
    condition     = aws_vpn_connection.this["sonatype"].static_routes_only == false && aws_vpn_connection.this["sonatype"].transit_gateway_id == "tgw-0aaaaaaaaaaaaaaa1"
    error_message = "Dynamic (BGP) routing, attached to the transit gateway."
  }
}

run "strong_crypto_on_both_tunnels" {
  command = plan

  assert {
    condition = (
      aws_vpn_connection.this["sonatype"].tunnel1_ike_versions == toset(["ikev2"]) &&
      aws_vpn_connection.this["sonatype"].tunnel2_ike_versions == toset(["ikev2"]) &&
      aws_vpn_connection.this["sonatype"].tunnel1_phase1_encryption_algorithms == toset(["AES256"]) &&
      aws_vpn_connection.this["sonatype"].tunnel2_phase2_integrity_algorithms == toset(["SHA2-256"]) &&
      aws_vpn_connection.this["sonatype"].tunnel1_phase1_dh_group_numbers == toset([14])
    )
    error_message = "IKEv2, AES-256, SHA2-256 and DH group 14 are expected on both tunnels."
  }
}

run "tunnel_inside_ranges_are_optional" {
  command = plan

  assert {
    condition     = aws_vpn_connection.this["motivity"].tunnel1_inside_cidr == "169.254.10.0/30" && aws_vpn_connection.this["motivity"].tunnel2_inside_cidr == "169.254.10.4/30"
    error_message = "Given inside ranges must be used."
  }
}

run "rejects_a_bad_address" {
  command = plan

  variables {
    connections = {
      bad = { customer_gateway_ip = "not-an-ip", bgp_asn = 65010 }
    }
  }

  expect_failures = [var.connections]
}

run "rejects_an_inside_range_outside_link_local" {
  command = plan

  variables {
    connections = {
      bad = { customer_gateway_ip = "203.0.113.10", bgp_asn = 65010, tunnel_inside_cidrs = ["10.0.0.0/30"] }
    }
  }

  expect_failures = [var.connections]
}
