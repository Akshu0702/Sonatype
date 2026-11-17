output "zone_id" {
  description = "Private hosted zone id."
  value       = aws_route53_zone.private.zone_id
}

output "zone_name" {
  description = "Private hosted zone name."
  value       = aws_route53_zone.private.name
}

output "resolver_endpoint_id" {
  description = "Inbound resolver endpoint id, or null when none was created."
  value       = var.resolver_inbound == null ? null : aws_route53_resolver_endpoint.inbound[0].id
}

output "resolver_ip_addresses" {
  description = "Addresses the offices send DNS queries to (give them to the offices' DNS administrators)."
  value       = var.resolver_inbound == null ? [] : [for a in aws_route53_resolver_endpoint.inbound[0].ip_address : a.ip]
}
