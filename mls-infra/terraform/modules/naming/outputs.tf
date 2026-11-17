output "name" {
  description = "The resource name: <project>-<resource-type>-<environment>-<function>-<id>."
  value       = local.name

  precondition {
    condition     = length(local.name) <= var.max_length
    error_message = "The name is longer than max_length. Use the short form from the naming standard and set name_long."
  }

  precondition {
    condition     = can(regex("^[a-z0-9]+(-[a-z0-9]+)*$", local.name))
    error_message = "The name must use lower case letters, digits and single hyphens only."
  }
}

output "tags" {
  description = "The standard tags, set by automation and never by hand."
  value       = local.tags
}

output "tags_with_name" {
  description = "The standard tags plus the Name tag that AWS uses to show the resource name."
  value       = merge(local.tags, { Name = local.name })
}
