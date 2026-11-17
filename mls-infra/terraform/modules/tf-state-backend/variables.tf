variable "bucket_name" {
  description = "State bucket name, from the naming module, for example mls-s3-dev-store-001."
  type        = string
}

variable "create_lock_table" {
  description = "Create a DynamoDB table for state locking. Set to false when the S3 native lock file is used instead (needs a newer Terraform; see the README)."
  type        = bool
  default     = true
}

variable "lock_table_name" {
  description = "Name of the lock table. Defaults to the bucket name with a -lock suffix."
  type        = string
  default     = null
}

variable "kms_key_arn" {
  description = "Customer-managed KMS key for the bucket and the lock table. Null uses AWS-managed encryption, for the first bootstrap before the key exists."
  type        = string
  default     = null
}

variable "noncurrent_version_expiration_days" {
  description = "Days to keep previous versions of a state file."
  type        = number
  default     = 90
}

variable "access_log_bucket" {
  description = "Bucket that receives server access logs for the state bucket. Null disables access logging."
  type        = string
  default     = null
}

variable "tags" {
  description = "Tags from the naming module."
  type        = map(string)
  default     = {}
}
