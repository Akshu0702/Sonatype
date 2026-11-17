variable "name" {
  description = "VPC name, from the naming module, for example mls-vpc-prod-mgmt-001."
  type        = string
}

variable "cidr_block" {
  description = "VPC address range, from the network plan (for example 10.40.0.0/20 for shared services)."
  type        = string

  validation {
    condition     = can(cidrhost(var.cidr_block, 0))
    error_message = "cidr_block must be a valid CIDR range."
  }
}

variable "azs" {
  description = "Availability zones, two or three."
  type        = list(string)

  validation {
    condition     = length(var.azs) >= 2 && length(var.azs) <= 3
    error_message = "Use two or three availability zones."
  }
}

variable "tiers" {
  description = <<-EOT
    Subnet tiers, keyed by name (for example app, data, tgw, public). For each tier:
    newbits is added to the VPC prefix length, and netnums lists one subnet number per availability zone,
    in the same order as azs. Numbers are explicit so ranges never overlap by accident.
    public = true gives the tier a route to the internet gateway (used only for NAT gateways).
  EOT
  type = map(object({
    newbits = number
    netnums = list(number)
    public  = optional(bool, false)
  }))

  validation {
    condition     = length(var.tiers) > 0
    error_message = "Define at least one tier."
  }

  validation {
    condition     = alltrue([for k, t in var.tiers : length(t.netnums) == length(var.azs)])
    error_message = "Each tier needs exactly one netnum per availability zone."
  }
}

variable "enable_nat" {
  description = "Create one NAT gateway per availability zone, in the NAT public tier."
  type        = bool
  default     = false

  validation {
    condition     = !var.enable_nat || contains(keys(var.tiers), var.nat_public_tier)
    error_message = "enable_nat needs a tier named by nat_public_tier."
  }
}

variable "nat_public_tier" {
  description = "Name of the public tier that holds the NAT gateways."
  type        = string
  default     = "public"
}

variable "nat_routed_tiers" {
  description = "Tiers whose default route goes to the NAT gateway in the same availability zone."
  type        = list(string)
  default     = []
}

variable "enable_s3_gateway_endpoint" {
  description = "Add an S3 gateway endpoint to the private route tables, so S3 traffic stays on the AWS network."
  type        = bool
  default     = true
}

variable "flow_logs_s3_arn" {
  description = "S3 bucket ARN for VPC flow logs. Null disables flow logs."
  type        = string
  default     = null
}

variable "tags" {
  description = "Tags from the naming module."
  type        = map(string)
  default     = {}
}
