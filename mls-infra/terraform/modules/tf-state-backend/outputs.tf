output "bucket_id" {
  description = "State bucket name."
  value       = aws_s3_bucket.state.id
}

output "bucket_arn" {
  description = "State bucket ARN, for IAM policies."
  value       = aws_s3_bucket.state.arn
}

output "lock_table_name" {
  description = "Lock table name, or null when the lock table is not created."
  value       = var.create_lock_table ? aws_dynamodb_table.lock[0].name : null
}

output "backend_config" {
  description = "Values for a backend.hcl file. Add a key per root module when you use it."
  value = {
    bucket         = aws_s3_bucket.state.id
    region         = data.aws_region.current.name
    encrypt        = true
    dynamodb_table = var.create_lock_table ? aws_dynamodb_table.lock[0].name : null
    kms_key_id     = var.kms_key_arn
  }
}
