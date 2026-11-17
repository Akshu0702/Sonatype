variable "name" {
  description = "Transit gateway name, from the naming module, for example mls-tgw-prod-mgmt-001."
  type        = string
}

variable "description" {
  description = "Description of the transit gateway."
  type        = string
  default     = "Sonatype Managed Lab transit gateway"
}

variable "amazon_side_asn" {
  description = "Private ASN of the AWS side of the BGP sessions."
  type        = number
  default     = 64512

  validation {
    condition     = (var.amazon_side_asn >= 64512 && var.amazon_side_asn <= 65534) || (var.amazon_side_asn >= 4200000000 && var.amazon_side_asn <= 4294967294)
    error_message = "amazon_side_asn must be in a private range: 64512-65534 or 4200000000-4294967294."
  }
}

variable "route_tables" {
  description = "Names of the transit gateway route tables, one per routing domain (for example onprem, shared, env1, env2)."
  type        = list(string)

  validation {
    condition     = length(var.route_tables) > 0 && length(var.route_tables) == length(distinct(var.route_tables))
    error_message = "Give at least one route table, with unique names."
  }
}

variable "vpc_attachments" {
  description = <<-EOT
    VPC attachments by name. route_table is the table the attachment is associated with: traffic coming
    from the VPC is looked up there. propagate_to lists the tables that learn this VPC's range, so it
    decides who can reach it.
  EOT
  type = map(object({
    vpc_id         = string
    subnet_ids     = list(string)
    route_table    = string
    propagate_to   = optional(list(string), [])
    dns_support    = optional(bool, true)
    appliance_mode = optional(bool, false)
  }))
  default = {}

  validation {
    condition = alltrue([
      for k, a in var.vpc_attachments :
      contains(var.route_tables, a.route_table) && alltrue([for r in a.propagate_to : contains(var.route_tables, r)])
    ])
    error_message = "Every route_table and propagate_to entry must be one of route_tables."
  }
}

variable "external_attachments" {
  description = "Attachments created elsewhere, for example by the VPN module, by name. Same meaning of route_table and propagate_to."
  type = map(object({
    attachment_id = string
    route_table   = string
    propagate_to  = optional(list(string), [])
  }))
  default = {}

  validation {
    condition = alltrue([
      for k, a in var.external_attachments :
      contains(var.route_tables, a.route_table) && alltrue([for r in a.propagate_to : contains(var.route_tables, r)])
    ])
    error_message = "Every route_table and propagate_to entry must be one of route_tables."
  }

  validation {
    condition     = length(setintersection(toset(keys(var.external_attachments)), toset(keys(var.vpc_attachments)))) == 0
    error_message = "An attachment name cannot be used in both vpc_attachments and external_attachments."
  }
}

variable "static_routes" {
  description = "Static routes in transit gateway route tables, for example a default route to the shared services attachment for central egress."
  type = map(object({
    route_table = string
    cidr        = string
    attachment  = optional(string)
    blackhole   = optional(bool, false)
  }))
  default = {}

  validation {
    condition     = alltrue([for k, r in var.static_routes : contains(var.route_tables, r.route_table)])
    error_message = "Every static route must use one of route_tables."
  }

  validation {
    condition = alltrue([
      for k, r in var.static_routes :
      r.blackhole || try(contains(concat(keys(var.vpc_attachments), keys(var.external_attachments)), r.attachment), false)
    ])
    error_message = "A static route needs an attachment that exists, unless it is a blackhole route."
  }
}

variable "vpc_routes" {
  description = "Routes to add in VPC route tables that send a destination range to the transit gateway, by name."
  type = map(object({
    route_table_id         = string
    destination_cidr_block = string
  }))
  default = {}
}

variable "tags" {
  description = "Tags from the naming module."
  type        = map(string)
  default     = {}
}
