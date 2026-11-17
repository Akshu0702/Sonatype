output "secret_arns" {
  description = "Secret ARNs by key (<service>/<purpose>)."
  value       = { for k, s in aws_secretsmanager_secret.this : k => s.arn }
}

output "secret_names" {
  description = "Secret names by key: <project>/<environment>/<service>/<purpose>."
  value       = { for k, s in aws_secretsmanager_secret.this : k => s.name }
}
