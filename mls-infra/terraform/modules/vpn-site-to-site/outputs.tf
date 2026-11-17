output "customer_gateway_ids" {
  description = "Customer gateway ids by site."
  value       = { for k, c in aws_customer_gateway.this : k => c.id }
}

output "connection_ids" {
  description = "VPN connection ids by site."
  value       = { for k, v in aws_vpn_connection.this : k => v.id }
}

output "transit_gateway_attachment_ids" {
  description = "Transit gateway attachment ids by site, for the transit-gateway module's external_attachments."
  value       = { for k, v in aws_vpn_connection.this : k => v.transit_gateway_attachment_id }
}

output "tunnel_addresses" {
  description = "The two AWS public endpoint addresses per site, to give to the site's network team."
  value       = { for k, v in aws_vpn_connection.this : k => [v.tunnel1_address, v.tunnel2_address] }
}

output "tunnel_metrics" {
  description = "CloudWatch dimensions (VpnId and TunnelIpAddress) for each tunnel, for the cloudwatch-alarms module."
  value = {
    for k, t in local.tunnels : k => {
      vpn_id            = aws_vpn_connection.this[t.connection].id
      tunnel_ip_address = t.number == 1 ? aws_vpn_connection.this[t.connection].tunnel1_address : aws_vpn_connection.this[t.connection].tunnel2_address
    }
  }
}
