variable "project" {
  description = "Project code, lower case."
  type        = string
  default     = "mls"

  validation {
    condition     = can(regex("^[a-z][a-z0-9]{1,9}$", var.project))
    error_message = "project must be 2 to 10 characters: lower case letters and digits, starting with a letter."
  }
}

variable "resource_type" {
  description = "Short code for the kind of resource, for example ec2, vpc, rds, s3, eks, kms."
  type        = string

  validation {
    condition     = can(regex("^[a-z][a-z0-9]{0,9}$", var.resource_type))
    error_message = "resource_type must be 1 to 10 characters: lower case letters and digits, starting with a letter."
  }
}

variable "environment" {
  description = "Where the resource runs. A new environment code needs a story, not a pipeline edit."
  type        = string

  validation {
    condition     = contains(["dev", "qa", "uat", "prod"], var.environment)
    error_message = "environment must be one of: dev, qa, uat, prod."
  }
}

variable "function" {
  description = "What the resource is for: one word from the agreed list."
  type        = string

  validation {
    condition     = contains(["web", "api", "db", "cache", "broker", "idp", "edge", "llm", "mgmt", "lab", "fw", "store"], var.function)
    error_message = "function must be one of: web, api, db, cache, broker, idp, edge, llm, mgmt, lab, fw, store."
  }
}

variable "id" {
  description = "Three-digit sequence, zero padded, unique within (type, environment, function). Never reused after a resource is deleted."
  type        = string
  default     = "001"

  validation {
    condition     = can(regex("^[0-9]{3}$", var.id))
    error_message = "id must be exactly three digits, for example 001."
  }
}

variable "max_length" {
  description = "Longest name the target resource allows. The name is rejected, not truncated, if it is longer."
  type        = number
  default     = 63
}

variable "owner" {
  description = "A team or role that owns the resource. Never a person."
  type        = string

  validation {
    condition     = length(trimspace(var.owner)) > 0 && !can(regex("[@ ]", var.owner))
    error_message = "owner must be a non-empty team or role name without spaces or an email address."
  }
}

variable "managed_by" {
  description = "The tool that created the resource."
  type        = string
  default     = "terraform"
}

variable "data_class" {
  description = "Which resources may hold tenant data."
  type        = string
  default     = "none"

  validation {
    condition     = contains(["none", "internal", "tenant-data"], var.data_class)
    error_message = "data_class must be one of: none, internal, tenant-data."
  }
}

variable "region" {
  description = "Provider region. Kept as a tag, never in the name, so a move does not rename."
  type        = string
  default     = null
}

variable "name_long" {
  description = "The full name, when a short form of the name is used because of a provider limit."
  type        = string
  default     = null
}

variable "extra_tags" {
  description = "Additional tags. The standard tags always win over these."
  type        = map(string)
  default     = {}
}
