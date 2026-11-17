output "arn" {
  description = "State machine ARN (without a version or alias)."
  value       = aws_sfn_state_machine.this.arn
}

output "name" {
  description = "State machine name."
  value       = aws_sfn_state_machine.this.name
}

output "version_arn" {
  description = "The version published by this change."
  value       = aws_sfn_state_machine.this.state_machine_version_arn
}

output "alias_arns" {
  description = "Alias name to alias ARN. Start executions through these."
  value       = { for k, a in aws_sfn_alias.this : k => a.arn }
}

output "role_arn" {
  description = "Role of the state machine."
  value       = aws_iam_role.this.arn
}

output "log_group_name" {
  description = "Where execution logs go."
  value       = aws_cloudwatch_log_group.this.name
}
