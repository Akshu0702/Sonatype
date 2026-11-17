data "aws_region" "current" {}

locals {
  # One entry per tier and availability zone, for example "app/ap-south-1a".
  subnets = merge([
    for tier, t in var.tiers : {
      for i, az in var.azs : "${tier}/${az}" => {
        tier   = tier
        az     = az
        cidr   = cidrsubnet(var.cidr_block, t.newbits, t.netnums[i])
        public = t.public
      }
    }
  ]...)

  has_public = anytrue([for k, t in var.tiers : t.public])
  nat_azs    = var.enable_nat ? toset(var.azs) : toset([])
}

resource "aws_vpc" "this" {
  cidr_block           = var.cidr_block
  enable_dns_support   = true
  enable_dns_hostnames = true

  tags = merge(var.tags, { Name = var.name })
}

# The default security group allows nothing, so nothing is exposed by accident.
resource "aws_default_security_group" "this" {
  vpc_id = aws_vpc.this.id

  tags = merge(var.tags, { Name = "${var.name}-default" })
}

resource "aws_internet_gateway" "this" {
  count = local.has_public ? 1 : 0

  vpc_id = aws_vpc.this.id

  tags = merge(var.tags, { Name = var.name })
}

resource "aws_subnet" "this" {
  for_each = local.subnets

  vpc_id                  = aws_vpc.this.id
  cidr_block              = each.value.cidr
  availability_zone       = each.value.az
  map_public_ip_on_launch = false

  tags = merge(var.tags, {
    Name = "${var.name}-${each.value.tier}-${substr(each.value.az, -1, 1)}"
    tier = each.value.tier
  })
}

resource "aws_route_table" "this" {
  for_each = local.subnets

  vpc_id = aws_vpc.this.id

  tags = merge(var.tags, {
    Name = "${var.name}-${each.value.tier}-${substr(each.value.az, -1, 1)}"
    tier = each.value.tier
  })
}

resource "aws_route_table_association" "this" {
  for_each = local.subnets

  subnet_id      = aws_subnet.this[each.key].id
  route_table_id = aws_route_table.this[each.key].id
}

resource "aws_route" "public_default" {
  for_each = { for k, s in local.subnets : k => s if s.public }

  route_table_id         = aws_route_table.this[each.key].id
  destination_cidr_block = "0.0.0.0/0"
  gateway_id             = aws_internet_gateway.this[0].id
}

resource "aws_eip" "nat" {
  for_each = local.nat_azs

  domain = "vpc"

  tags = merge(var.tags, { Name = "${var.name}-nat-${substr(each.key, -1, 1)}" })
}

resource "aws_nat_gateway" "this" {
  for_each = local.nat_azs

  allocation_id = aws_eip.nat[each.key].id
  subnet_id     = aws_subnet.this["${var.nat_public_tier}/${each.key}"].id

  tags = merge(var.tags, { Name = "${var.name}-nat-${substr(each.key, -1, 1)}" })

  depends_on = [aws_internet_gateway.this]
}

resource "aws_route" "nat_default" {
  for_each = { for k, s in local.subnets : k => s if var.enable_nat && contains(var.nat_routed_tiers, s.tier) }

  route_table_id         = aws_route_table.this[each.key].id
  destination_cidr_block = "0.0.0.0/0"
  nat_gateway_id         = aws_nat_gateway.this[each.value.az].id
}

resource "aws_vpc_endpoint" "s3" {
  count = var.enable_s3_gateway_endpoint ? 1 : 0

  vpc_id            = aws_vpc.this.id
  service_name      = "com.amazonaws.${data.aws_region.current.name}.s3"
  vpc_endpoint_type = "Gateway"
  route_table_ids   = [for k, s in local.subnets : aws_route_table.this[k].id if !s.public]

  tags = merge(var.tags, { Name = "${var.name}-s3" })
}

resource "aws_flow_log" "this" {
  count = var.flow_logs_s3_arn == null ? 0 : 1

  vpc_id               = aws_vpc.this.id
  traffic_type         = "ALL"
  log_destination_type = "s3"
  log_destination      = var.flow_logs_s3_arn

  tags = merge(var.tags, { Name = "${var.name}-flow" })
}
