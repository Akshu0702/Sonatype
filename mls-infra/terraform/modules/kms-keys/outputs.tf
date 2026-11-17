output "key_arns" {
  description = "Key ARNs by purpose."
  value       = { for k, v in aws_kms_key.this : k => v.arn }
}

output "key_ids" {
  description = "Key ids by purpose."
  value       = { for k, v in aws_kms_key.this : k => v.key_id }
}

output "alias_names" {
  description = "Alias names by purpose: alias/<name_prefix>-<purpose>."
  value       = { for k, v in aws_kms_alias.this : k => v.name }
}
