variable "bucket_name" {
  description = "Bucket name, from the naming module, for example mls-s3-prod-store-002. Add a short account suffix if the name collides."
  type        = string

  validation {
    condition     = can(regex("^[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]$", var.bucket_name))
    error_message = "bucket_name must be 3 to 63 characters: lower case letters, digits, hyphens and dots."
  }
}

variable "kms_key_arn" {
  description = "Customer-managed KMS key for the bucket. Null uses AWS-managed encryption (AES256), for buckets that a service writes to and cannot use a key."
  type        = string
  default     = null
}

variable "versioning_enabled" {
  description = "Keep previous versions of objects. Must stay on when replication or object lock is used."
  type        = bool
  default     = true

  validation {
    condition     = var.versioning_enabled || (var.replication == null && var.object_lock == null)
    error_message = "Replication and object lock need versioning to be enabled."
  }
}

variable "force_destroy" {
  description = "Allow Terraform to delete a bucket that still holds objects. Keep false outside throwaway environments."
  type        = bool
  default     = false
}

variable "object_lock" {
  description = "Object lock with a default retention, for data that must not be changed or deleted (for example exported audit records). Cannot be added to an existing bucket."
  type = object({
    mode = string
    days = number
  })
  default = null

  validation {
    condition     = var.object_lock == null ? true : (contains(["GOVERNANCE", "COMPLIANCE"], var.object_lock.mode) && var.object_lock.days >= 1)
    error_message = "object_lock mode must be GOVERNANCE or COMPLIANCE, with at least one day."
  }
}

variable "lifecycle_rules" {
  description = "Lifecycle rules in addition to the built-in rule that cleans up incomplete multipart uploads."
  type = list(object({
    id                         = string
    prefix                     = optional(string, "")
    expiration_days            = optional(number)
    noncurrent_expiration_days = optional(number)
    transition_ia_days         = optional(number)
    transition_glacier_days    = optional(number)
  }))
  default = []
}

variable "replication" {
  description = "Cross-region replication of every object to another bucket, for example in the DR region. The replication role is created elsewhere."
  type = object({
    role_arn                = string
    destination_bucket_arn  = string
    destination_kms_key_arn = optional(string)
  })
  default = null
}

variable "access_log_bucket" {
  description = "Bucket that receives server access logs. Null disables access logging."
  type        = string
  default     = null
}

variable "extra_policy_json" {
  description = "Additional bucket policy statements as JSON, merged with the built-in TLS-only statement. Use for service access such as VPC flow logs."
  type        = string
  default     = null
}

variable "tags" {
  description = "Tags from the naming module."
  type        = map(string)
  default     = {}
}
