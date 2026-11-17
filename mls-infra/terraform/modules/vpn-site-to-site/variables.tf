variable "name" {
  description = "Name prefix, from the naming module, for example mls-vpn-prod-mgmt-001."
  type        = string
}

variable "transit_gateway_id" {
  description = "Transit gateway the VPN connections attach to."
  type        = string
}

variable "connections" {
  description = <<-EOT
    One entry per office or site, by name (for example sonatype, motivity). Each creates a customer gateway
    and a VPN connection with two tunnels, using IKEv2 and BGP.
  EOT
  type = map(object({
    customer_gateway_ip = string
    bgp_asn             = number
    tunnel_inside_cidrs = optional(list(string), [])
    enable_acceleration = optional(bool, false)
  }))

  validation {
    condition     = alltrue([for k, c in var.connections : can(regex("^[0-9]{1,3}(\\.[0-9]{1,3}){3}$", c.customer_gateway_ip))])
    error_message = "customer_gateway_ip must be a public IPv4 address."
  }

  validation {
    condition     = alltrue([for k, c in var.connections : c.bgp_asn >= 1 && c.bgp_asn <= 4294967294])
    error_message = "bgp_asn must be between 1 and 4294967294."
  }

  validation {
    condition = alltrue([
      for k, c in var.connections :
      length(c.tunnel_inside_cidrs) <= 2 && alltrue([for cidr in c.tunnel_inside_cidrs : can(regex("^169\\.254\\.[0-9]{1,3}\\.[0-9]{1,3}/30$", cidr))])
    ])
    error_message = "tunnel_inside_cidrs holds at most two /30 ranges inside 169.254.0.0/16."
  }
}

variable "tags" {
  description = "Tags from the naming module."
  type        = map(string)
  default     = {}
}
