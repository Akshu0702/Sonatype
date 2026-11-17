locals {
  extra_vpcs = { for i, v in slice(var.vpc_ids, 1, length(var.vpc_ids)) : tostring(i) => v }

  resolver_rules = var.resolver_inbound == null ? {} : merge([
    for c in var.resolver_inbound.allowed_cidrs : {
      "tcp/${c}" = { cidr = c, protocol = "tcp" }
      "udp/${c}" = { cidr = c, protocol = "udp" }
    }
  ]...)
}

resource "aws_route53_zone" "private" {
  name    = var.zone_name
  comment = var.comment

  vpc {
    vpc_id = var.vpc_ids[0]
  }

  # Further VPCs are attached below; do not let this block fight them.
  lifecycle {
    ignore_changes = [vpc]
  }

  tags = merge(var.tags, { Name = var.zone_name })
}

resource "aws_route53_zone_association" "extra" {
  for_each = local.extra_vpcs

  zone_id = aws_route53_zone.private.zone_id
  vpc_id  = each.value
}

resource "aws_route53_record" "this" {
  for_each = var.records

  zone_id = aws_route53_zone.private.zone_id
  name    = each.value.name
  type    = each.value.type
  ttl     = each.value.ttl
  records = each.value.records
}

resource "aws_security_group" "resolver" {
  count = var.resolver_inbound == null ? 0 : 1

  name        = var.resolver_inbound.name
  description = "Inbound DNS queries from office networks"
  vpc_id      = var.resolver_inbound.vpc_id

  tags = merge(var.tags, { Name = var.resolver_inbound.name })
}

resource "aws_vpc_security_group_ingress_rule" "resolver" {
  for_each = local.resolver_rules

  security_group_id = aws_security_group.resolver[0].id
  cidr_ipv4         = each.value.cidr
  ip_protocol       = each.value.protocol
  from_port         = 53
  to_port           = 53
  description       = "DNS ${each.value.protocol} from ${each.value.cidr}"
}

resource "aws_route53_resolver_endpoint" "inbound" {
  count = var.resolver_inbound == null ? 0 : 1

  name               = var.resolver_inbound.name
  direction          = "INBOUND"
  security_group_ids = [aws_security_group.resolver[0].id]

  dynamic "ip_address" {
    for_each = { for i, s in var.resolver_inbound.subnet_ids : tostring(i) => s }

    content {
      subnet_id = ip_address.value
      ip        = length(var.resolver_inbound.private_ips) > 0 ? var.resolver_inbound.private_ips[tonumber(ip_address.key)] : null
    }
  }

  tags = merge(var.tags, { Name = var.resolver_inbound.name })
}
