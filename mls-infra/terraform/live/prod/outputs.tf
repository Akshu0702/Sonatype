output "enabled" {
  description = "False means nothing for this environment exists."
  value       = var.enabled
}

output "cluster_name" {
  value = one(module.environment[*].cluster_name)
}

output "database_address" {
  value = one(module.environment[*].database_address)
}

output "registry_bucket" {
  value = one(module.environment[*].registry_bucket)
}

output "harbor_role_arn" {
  value = one(module.environment[*].harbor_role_arn)
}

output "addon_role_arns" {
  value = one(module.environment[*].addon_role_arns)
}

output "secret_names" {
  value = one(module.environment[*].secret_names)
}
