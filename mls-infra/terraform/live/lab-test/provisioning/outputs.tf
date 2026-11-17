output "provision_alias_arn" {
  description = "Start provisioning through this alias. Input: environmentId, configuration, owner, ticket, ttlHours (optional)."
  value       = module.provisioning.provision_alias_arn
}

output "reaper_alias_arn" {
  description = "Start the ttl-reaper through this alias to tear down now (set the expires-at tag to the current time first)."
  value       = module.provisioning.reaper_alias_arn
}

output "configurations" {
  description = "What a request may name, and the image and size each resolves to."
  value       = module.provisioning.configurations
}

output "uses_fallback_image" {
  description = "True while environments launch from plain Ubuntu, which is NOT hardened."
  value       = module.provisioning.uses_fallback_image
}

output "api_policy_json" {
  description = "Permissions for the role the provisioning API runs as."
  value       = module.provisioning.api_policy_json
}

output "start_image_build_command" {
  description = "How to build the hardened image once the archive is uploaded."
  value       = var.enable_image_builder ? module.image[0].start_build_command : null
}

output "alerts_topic_arn" {
  description = "Topic told when an execution fails."
  value       = aws_sns_topic.alerts.arn
}
