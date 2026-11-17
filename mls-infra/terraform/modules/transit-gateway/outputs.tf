output "transit_gateway_id" {
  description = "Transit gateway id."
  value       = aws_ec2_transit_gateway.this.id
}

output "transit_gateway_arn" {
  description = "Transit gateway ARN."
  value       = aws_ec2_transit_gateway.this.arn
}

output "route_table_ids" {
  description = "Transit gateway route table ids by name."
  value       = { for k, t in aws_ec2_transit_gateway_route_table.this : k => t.id }
}

output "vpc_attachment_ids" {
  description = "VPC attachment ids by name."
  value       = { for k, a in aws_ec2_transit_gateway_vpc_attachment.this : k => a.id }
}
