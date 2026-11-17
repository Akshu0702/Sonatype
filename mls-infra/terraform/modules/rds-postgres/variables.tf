variable "identifier" {
  description = "Instance identifier, from the naming module, for example mls-rds-prod-db-001."
  type        = string

  validation {
    condition     = can(regex("^[a-z][a-z0-9]*(-[a-z0-9]+)*$", var.identifier)) && length(var.identifier) <= 63
    error_message = "identifier must start with a letter, use lower case letters, digits and single hyphens, and be at most 63 characters."
  }
}

variable "engine_version" {
  description = "PostgreSQL version. Deliberately has no default: the version is a decision, pinned by the caller."
  type        = string
}

variable "parameter_group_family" {
  description = "Parameter group family matching engine_version, for example postgres16."
  type        = string
}

variable "instance_class" {
  description = "Instance class. The Gate 1 BOM uses db.m6g.large."
  type        = string
  default     = "db.m6g.large"
}

variable "allocated_storage" {
  description = "Storage in GiB."
  type        = number
  default     = 100
}

variable "max_allocated_storage" {
  description = "Upper limit for storage autoscaling in GiB. Null disables autoscaling."
  type        = number
  default     = null
}

variable "storage_type" {
  description = "Storage type."
  type        = string
  default     = "gp3"
}

variable "kms_key_id" {
  description = "Customer-managed KMS key for storage, performance insights and the master user secret."
  type        = string
}

variable "db_name" {
  description = "Initial database name. Null creates none."
  type        = string
  default     = null
}

variable "username" {
  description = "Master user name. The password is generated and held by RDS in Secrets Manager; it never enters Terraform."
  type        = string
  default     = "dbadmin"
}

variable "vpc_id" {
  description = "VPC of the instance."
  type        = string
}

variable "subnet_ids" {
  description = "Data subnets in at least two availability zones."
  type        = list(string)

  validation {
    condition     = length(var.subnet_ids) >= 2
    error_message = "Give subnets in at least two availability zones."
  }
}

variable "allowed_security_group_ids" {
  description = "Security groups allowed to connect on port 5432."
  type        = list(string)
  default     = []
}

variable "allowed_cidrs" {
  description = "Address ranges allowed to connect on port 5432."
  type        = list(string)
  default     = []
}

variable "multi_az" {
  description = "Run a standby in another availability zone."
  type        = bool
  default     = true
}

variable "backup_retention_period" {
  description = "Days to keep automated backups. The Gate 1 design uses 30."
  type        = number
  default     = 30

  validation {
    condition     = var.backup_retention_period >= 7 && var.backup_retention_period <= 35
    error_message = "backup_retention_period must be between 7 and 35 days."
  }
}

variable "backup_window" {
  description = "Daily backup window, UTC."
  type        = string
  default     = "21:00-22:00"
}

variable "maintenance_window" {
  description = "Weekly maintenance window, UTC."
  type        = string
  default     = "sun:22:00-sun:23:00"
}

variable "deletion_protection" {
  description = "Refuse deletion of the instance."
  type        = bool
  default     = true
}

variable "performance_insights_enabled" {
  description = "Enable Performance Insights."
  type        = bool
  default     = true
}

variable "extra_parameters" {
  description = "Further parameter group settings."
  type = list(object({
    name         = string
    value        = string
    apply_method = optional(string, "immediate")
  }))
  default = []
}

variable "tags" {
  description = "Tags from the naming module."
  type        = map(string)
  default     = {}
}
