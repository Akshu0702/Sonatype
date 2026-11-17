variable "enabled" {
  description = "Build this environment. False by default: the stack is code only until the delivery lead approves creating it."
  type        = bool
  default     = false
}

variable "confirm" {
  description = "Must be exactly create-uat-in-aws when enabled is true."
  type        = string
  default     = ""
}

variable "region" {
  description = "The only region used."
  type        = string
  default     = "ap-south-1"

  validation {
    condition     = var.region == "ap-south-1"
    error_message = "Only ap-south-1 (Mumbai) is allowed."
  }
}

variable "account_id" {
  description = "The only account this may run against. The provider refuses any other. Change it here to move the environment to its own account."
  type        = string
  default     = "096631344162"

  validation {
    condition     = can(regex("^[0-9]{12}$", var.account_id))
    error_message = "account_id must be a 12-digit account number."
  }
}

variable "admin_role_arn" {
  description = "IAM role that administers the cluster (for example the Identity Center administrator role). Required when enabled."
  type        = string
  default     = null
}

variable "cluster_mode" {
  description = "dedicated (its own VPC and cluster, the default) or shared (reuse a cluster, separated by namespaces)."
  type        = string
  default     = "dedicated"
}

variable "shared_cluster" {
  description = "The cluster to reuse when cluster_mode is shared."
  type = object({
    cluster_name               = string
    vpc_id                     = string
    data_subnet_ids            = list(string)
    node_security_group_id     = string
    oidc_provider_arn          = string
    oidc_issuer                = string
    external_secrets_role_name = optional(string)
  })
  default = null
}

variable "cidr_block" {
  description = "VPC range. Proposed, not yet in the network plan: it must be added, and agreed with the customer's network team before the VPN is configured, before this stack is enabled."
  type        = string
  default     = "10.43.0.0/20"
}

variable "azs" {
  description = "Availability zones."
  type        = list(string)
  default     = ["ap-south-1a", "ap-south-1b"]
}

variable "kubernetes_version" {
  description = "Kubernetes version. Proposed, not decided: 1.36 is in standard support until 2027-08 (1.34 ends 2026-12-02). Check that the add-on chart versions support it first."
  type        = string
  default     = "1.36"
}

variable "node_groups" {
  description = "Node groups. Sizing is a planning figure, to be reviewed against the real load."
  type = map(object({
    instance_types = list(string)
    min_size       = number
    desired_size   = number
    max_size       = number
    disk_size_gb   = optional(number, 100)
    capacity_type  = optional(string, "ON_DEMAND")
    labels         = optional(map(string), {})
  }))
  default = {
    apps = { instance_types = ["m6i.large"], min_size = 2, desired_size = 2, max_size = 4, disk_size_gb = 100, labels = { workload = "apps" } }
  }
}

variable "db" {
  description = "The PostgreSQL instance. The engine version is pinned here and is a decision (the platform assumes 16)."
  type = object({
    engine_version          = string
    instance_class          = optional(string)
    allocated_storage       = optional(number)
    max_allocated_storage   = optional(number)
    multi_az                = optional(bool)
    backup_retention_period = optional(number)
    deletion_protection     = optional(bool)
  })
  default = {
    engine_version          = "16.15"
    instance_class          = "db.m6g.large"
    allocated_storage       = 100
    max_allocated_storage   = 300
    multi_az                = false
    backup_retention_period = 14
    deletion_protection     = true
  }
}
