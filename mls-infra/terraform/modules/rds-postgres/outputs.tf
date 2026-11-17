output "address" {
  description = "Instance endpoint address."
  value       = aws_db_instance.this.address
}

output "port" {
  description = "Instance port."
  value       = aws_db_instance.this.port
}

output "arn" {
  description = "Instance ARN."
  value       = aws_db_instance.this.arn
}

output "security_group_id" {
  description = "Security group of the instance."
  value       = aws_security_group.this.id
}

output "master_user_secret_arn" {
  description = "ARN of the Secrets Manager secret that holds the master password, created and rotated by RDS."
  value       = try(aws_db_instance.this.master_user_secret[0].secret_arn, null)
}
