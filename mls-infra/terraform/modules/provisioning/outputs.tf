output "provision_alias_arn" {
  description = "Start provisioning through this alias (input: environmentId, configuration, owner, ticket, ttlHours)."
  value       = module.provision.alias_arns[var.environment]
}

output "reaper_alias_arn" {
  description = "Start the ttl-reaper through this alias to tear down now (set the expires-at tag to the current time first)."
  value       = module.reaper.alias_arns[var.environment]
}

output "state_machine_arns" {
  description = "The two state machines."
  value = {
    provision = module.provision.arn
    reaper    = module.reaper.arn
  }
}

output "instance_profile_name" {
  description = "Instance profile of an environment."
  value       = aws_iam_instance_profile.environment.name
}

output "environment_role_arn" {
  description = "Role of an environment."
  value       = aws_iam_role.environment.arn
}

output "security_group_id" {
  description = "Security group of an environment."
  value       = aws_security_group.environments.id
}

output "configurations" {
  description = "The configuration keys a request may name, with the image and size each resolves to."
  value       = { for k, c in local.catalogue : k => { ami_id = c.amiId, instance_type = c.instanceType, root_volume_gb = c.rootVolumeGb } }
}

output "image_source" {
  description = "given, hardened-lookup, or ubuntu-fallback."
  value       = local.image_source
}

output "uses_fallback_image" {
  description = "True when environments launch from plain Ubuntu, which is NOT hardened. Only for trying the flow."
  value       = local.image_source == "ubuntu-fallback"
}

output "api_policy_json" {
  description = "What the provisioning API's role needs: start executions, read them, read environments, move the expiry."
  value       = local.api_policy
}

output "rendered_definitions" {
  description = "The two state machines after the placeholders are filled in, as deployed. For review and for the local tests."
  value = {
    provision = local.provision_definition
    reaper    = local.reaper_definition
  }

  precondition {
    condition     = can(jsondecode(local.provision_definition).StartAt) && can(jsondecode(local.reaper_definition).StartAt)
    error_message = "A state machine template did not render to a valid state machine."
  }
}

output "provision_policy_json" {
  description = "What the provision-environment state machine's role may do. Shown so a reviewer can read it without applying anything."
  value       = local.provision_policy
}

output "reaper_policy_json" {
  description = "What the ttl-reaper's role may do."
  value       = local.reaper_policy
}
