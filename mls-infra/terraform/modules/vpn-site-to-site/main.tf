locals {
  tunnels = merge([
    for k in keys(var.connections) : {
      "${k}-1" = { connection = k, number = 1 }
      "${k}-2" = { connection = k, number = 2 }
    }
  ]...)
}

resource "aws_customer_gateway" "this" {
  for_each = var.connections

  bgp_asn    = tostring(each.value.bgp_asn)
  ip_address = each.value.customer_gateway_ip
  type       = "ipsec.1"

  tags = merge(var.tags, { Name = "${var.name}-${each.key}" })
}

resource "aws_vpn_connection" "this" {
  for_each = var.connections

  customer_gateway_id = aws_customer_gateway.this[each.key].id
  transit_gateway_id  = var.transit_gateway_id
  type                = "ipsec.1"
  static_routes_only  = false
  enable_acceleration = each.value.enable_acceleration

  tunnel1_inside_cidr = try(each.value.tunnel_inside_cidrs[0], null)
  tunnel2_inside_cidr = try(each.value.tunnel_inside_cidrs[1], null)

  # IKEv2 with AES-256, SHA-256 and DH group 14, on both tunnels.
  tunnel1_ike_versions                 = ["ikev2"]
  tunnel1_phase1_encryption_algorithms = ["AES256"]
  tunnel1_phase1_integrity_algorithms  = ["SHA2-256"]
  tunnel1_phase1_dh_group_numbers      = [14]
  tunnel1_phase2_encryption_algorithms = ["AES256"]
  tunnel1_phase2_integrity_algorithms  = ["SHA2-256"]
  tunnel1_phase2_dh_group_numbers      = [14]
  tunnel1_dpd_timeout_action           = "restart"

  tunnel2_ike_versions                 = ["ikev2"]
  tunnel2_phase1_encryption_algorithms = ["AES256"]
  tunnel2_phase1_integrity_algorithms  = ["SHA2-256"]
  tunnel2_phase1_dh_group_numbers      = [14]
  tunnel2_phase2_encryption_algorithms = ["AES256"]
  tunnel2_phase2_integrity_algorithms  = ["SHA2-256"]
  tunnel2_phase2_dh_group_numbers      = [14]
  tunnel2_dpd_timeout_action           = "restart"

  tags = merge(var.tags, { Name = "${var.name}-${each.key}" })
}
