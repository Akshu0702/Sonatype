variable "zone_name" {
  description = "Private zone name, for example lab.internal."
  type        = string

  validation {
    condition     = can(regex("^[a-z0-9]([a-z0-9-]*[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)+$", var.zone_name))
    error_message = "zone_name must be a lower case domain name with at least two labels."
  }
}

variable "comment" {
  description = "Zone comment."
  type        = string
  default     = "Sonatype Managed Lab private zone"
}

variable "vpc_ids" {
  description = "VPCs that resolve the zone. The first one creates the zone; the others are associated with it."
  type        = list(string)

  validation {
    condition     = length(var.vpc_ids) >= 1
    error_message = "Give at least one VPC."
  }
}

variable "resolver_inbound" {
  description = <<-EOT
    Inbound resolver endpoint, so office networks can resolve lab names over the VPN. Give two or more subnets
    in different availability zones, and the office ranges allowed to query (port 53). Null creates no endpoint.
    private_ips, if given, has one address per subnet in the same order.
  EOT
  type = object({
    name          = string
    vpc_id        = string
    subnet_ids    = list(string)
    allowed_cidrs = list(string)
    private_ips   = optional(list(string), [])
  })
  default = null

  validation {
    condition     = var.resolver_inbound == null ? true : length(var.resolver_inbound.subnet_ids) >= 2
    error_message = "An inbound resolver endpoint needs at least two subnets."
  }

  validation {
    condition     = var.resolver_inbound == null ? true : (length(var.resolver_inbound.private_ips) == 0 || length(var.resolver_inbound.private_ips) == length(var.resolver_inbound.subnet_ids))
    error_message = "private_ips must be empty or have one address per subnet."
  }

  validation {
    condition     = var.resolver_inbound == null ? true : (length(var.resolver_inbound.allowed_cidrs) > 0 && alltrue([for c in var.resolver_inbound.allowed_cidrs : can(cidrhost(c, 0))]))
    error_message = "allowed_cidrs must list at least one valid CIDR range."
  }
}

variable "records" {
  description = "Records in the private zone, by name."
  type = map(object({
    name    = string
    type    = string
    ttl     = optional(number, 300)
    records = list(string)
  }))
  default = {}
}

variable "tags" {
  description = "Tags from the naming module."
  type        = map(string)
  default     = {}
}
