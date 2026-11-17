locals {
  attachment_ids = merge(
    { for k, a in aws_ec2_transit_gateway_vpc_attachment.this : k => a.id },
    { for k, a in var.external_attachments : k => a.attachment_id },
  )

  associations = merge(
    { for k, a in var.vpc_attachments : k => a.route_table },
    { for k, a in var.external_attachments : k => a.route_table },
  )

  propagation_lists = merge(
    { for k, a in var.vpc_attachments : k => a.propagate_to },
    { for k, a in var.external_attachments : k => a.propagate_to },
  )

  # One entry per (attachment, route table that learns its range), for example "env1->shared".
  propagations = merge([
    for att, tables in local.propagation_lists : {
      for rt in tables : "${att}->${rt}" => { attachment = att, route_table = rt }
    }
  ]...)
}

resource "aws_ec2_transit_gateway" "this" {
  description     = var.description
  amazon_side_asn = var.amazon_side_asn

  # Nothing is associated or propagated by default: every path is declared here.
  default_route_table_association = "disable"
  default_route_table_propagation = "disable"
  auto_accept_shared_attachments  = "disable"
  dns_support                     = "enable"
  vpn_ecmp_support                = "enable"

  tags = merge(var.tags, { Name = var.name })
}

resource "aws_ec2_transit_gateway_route_table" "this" {
  for_each = toset(var.route_tables)

  transit_gateway_id = aws_ec2_transit_gateway.this.id

  tags = merge(var.tags, { Name = "${var.name}-${each.key}" })
}

resource "aws_ec2_transit_gateway_vpc_attachment" "this" {
  for_each = var.vpc_attachments

  transit_gateway_id     = aws_ec2_transit_gateway.this.id
  vpc_id                 = each.value.vpc_id
  subnet_ids             = each.value.subnet_ids
  dns_support            = each.value.dns_support ? "enable" : "disable"
  appliance_mode_support = each.value.appliance_mode ? "enable" : "disable"

  transit_gateway_default_route_table_association = false
  transit_gateway_default_route_table_propagation = false

  tags = merge(var.tags, { Name = "${var.name}-${each.key}" })
}

resource "aws_ec2_transit_gateway_route_table_association" "this" {
  for_each = local.associations

  transit_gateway_attachment_id  = local.attachment_ids[each.key]
  transit_gateway_route_table_id = aws_ec2_transit_gateway_route_table.this[each.value].id
}

resource "aws_ec2_transit_gateway_route_table_propagation" "this" {
  for_each = local.propagations

  transit_gateway_attachment_id  = local.attachment_ids[each.value.attachment]
  transit_gateway_route_table_id = aws_ec2_transit_gateway_route_table.this[each.value.route_table].id
}

resource "aws_ec2_transit_gateway_route" "static" {
  for_each = var.static_routes

  destination_cidr_block         = each.value.cidr
  transit_gateway_route_table_id = aws_ec2_transit_gateway_route_table.this[each.value.route_table].id
  blackhole                      = each.value.blackhole
  transit_gateway_attachment_id  = each.value.blackhole ? null : local.attachment_ids[each.value.attachment]
}

resource "aws_route" "vpc" {
  for_each = var.vpc_routes

  route_table_id         = each.value.route_table_id
  destination_cidr_block = each.value.destination_cidr_block
  transit_gateway_id     = aws_ec2_transit_gateway.this.id

  depends_on = [aws_ec2_transit_gateway_vpc_attachment.this]
}
