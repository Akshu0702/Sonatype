variable "name_prefix" {
  description = "Prefix for the key aliases, following the standard: mls-<environment>, giving alias/mls-<environment>-<purpose>."
  type        = string

  validation {
    condition     = can(regex("^[a-z][a-z0-9-]{1,30}$", var.name_prefix))
    error_message = "name_prefix must be lower case letters, digits and hyphens, starting with a letter."
  }
}

variable "keys" {
  description = <<-EOT
    Customer-managed keys to create, keyed by purpose (for example secrets, s3, rds, ebs, efs, logs).
    admins manage the key but cannot use it; users can use it for encryption but cannot manage it.
    Leave both empty to rely on IAM policies alone through the account root statement.
    A custom policy replaces the generated one.
  EOT
  type = map(object({
    description             = string
    deletion_window_in_days = optional(number, 30)
    enable_key_rotation     = optional(bool, true)
    admins                  = optional(list(string), [])
    users                   = optional(list(string), [])
    policy                  = optional(string)
  }))

  validation {
    condition     = alltrue([for k, v in var.keys : can(regex("^[a-z][a-z0-9-]{0,30}$", k))])
    error_message = "Each key name must be lower case letters, digits and hyphens, starting with a letter."
  }

  validation {
    condition     = alltrue([for k, v in var.keys : v.deletion_window_in_days >= 7 && v.deletion_window_in_days <= 30])
    error_message = "deletion_window_in_days must be between 7 and 30."
  }
}

variable "tags" {
  description = "Tags from the naming module."
  type        = map(string)
  default     = {}
}
