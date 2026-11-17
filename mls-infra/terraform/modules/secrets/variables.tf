variable "project" {
  description = "Project code."
  type        = string
  default     = "mls"
}

variable "environment" {
  description = "Environment code."
  type        = string

  validation {
    condition     = contains(["dev", "qa", "uat", "prod"], var.environment)
    error_message = "environment must be one of: dev, qa, uat, prod."
  }
}

variable "secrets" {
  description = <<-EOT
    Secrets to create, keyed by "<service>/<purpose>" (for example iam/db-password). The secret name becomes
    <project>/<environment>/<service>/<purpose>. This module creates the secret only. It never sets a value:
    values are set by the owning service, by RDS, or by an operator, so they never enter Terraform state.
  EOT
  type = map(object({
    description             = string
    kms_key_id              = string
    recovery_window_in_days = optional(number, 30)
    rotation_lambda_arn     = optional(string)
    rotation_days           = optional(number, 30)
    replica_regions = optional(list(object({
      region     = string
      kms_key_id = optional(string)
    })), [])
    policy = optional(string)
  }))
  default = {}

  validation {
    condition     = alltrue([for k, s in var.secrets : can(regex("^[a-z0-9-]+/[a-z0-9-]+$", k))])
    error_message = "Each secret key must be <service>/<purpose> in lower case letters, digits and hyphens."
  }

  validation {
    condition     = alltrue([for k, s in var.secrets : s.recovery_window_in_days >= 7 && s.recovery_window_in_days <= 30])
    error_message = "recovery_window_in_days must be between 7 and 30, so a deleted secret can be recovered."
  }

  validation {
    condition     = alltrue([for k, s in var.secrets : length(trimspace(s.description)) > 0 && length(trimspace(s.kms_key_id)) > 0])
    error_message = "Every secret needs a description and a KMS key."
  }
}

variable "tags" {
  description = "Tags from the naming module."
  type        = map(string)
  default     = {}
}
