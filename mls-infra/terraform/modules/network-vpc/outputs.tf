output "vpc_id" {
  description = "VPC id."
  value       = aws_vpc.this.id
}

output "vpc_cidr_block" {
  description = "VPC address range."
  value       = aws_vpc.this.cidr_block
}

output "subnet_ids" {
  description = "Subnet ids by tier, in the order of azs."
  value = {
    for tier in keys(var.tiers) : tier => [
      for az in var.azs : aws_subnet.this["${tier}/${az}"].id
    ]
  }
}

output "route_table_ids" {
  description = "Route table ids by tier, in the order of azs. Use them to add routes, for example to a Transit Gateway."
  value = {
    for tier in keys(var.tiers) : tier => [
      for az in var.azs : aws_route_table.this["${tier}/${az}"].id
    ]
  }
}

output "nat_gateway_ids" {
  description = "NAT gateway ids by availability zone."
  value       = { for az, n in aws_nat_gateway.this : az => n.id }
}

output "internet_gateway_id" {
  description = "Internet gateway id, or null when there is no public tier."
  value       = local.has_public ? aws_internet_gateway.this[0].id : null
}
