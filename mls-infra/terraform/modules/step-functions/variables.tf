variable "name" {
  description = "State machine name, from the naming standard, for example mls-sfn-dev-lab-001. Also names the role and the log group."
  type        = string

  validation {
    condition     = can(regex("^mls-[a-z0-9]+(-[a-z0-9]+)*$", var.name)) && length(var.name) <= 80
    error_message = "name must start with mls- and use lower case letters, digits and single hyphens (at most 80 characters)."
  }
}

variable "tags" {
  description = "Tags from the naming module."
  type        = map(string)
  default     = {}
}

variable "region" {
  description = "The only region used."
  type        = string
  default     = "ap-south-1"

  validation {
    condition     = var.region == "ap-south-1"
    error_message = "Only ap-south-1 (Mumbai) is allowed."
  }
}

variable "account_id" {
  description = "The account the state machine runs in. The role can only be assumed by a state machine of this account."
  type        = string

  validation {
    condition     = can(regex("^[0-9]{12}$", var.account_id))
    error_message = "account_id must be a 12-digit account number."
  }
}

variable "definition" {
  description = "The Amazon States Language document, already filled in. It comes from mls-automation-api/statemachines."
  type        = string

  validation {
    condition     = can(jsondecode(var.definition)) && can(jsondecode(var.definition).StartAt) && can(jsondecode(var.definition).States)
    error_message = "definition must be a JSON document with StartAt and States."
  }
}

variable "policy_json" {
  description = "The permissions of the state machine's role: a policy document, limited to what its tasks do and nothing else."
  type        = string

  validation {
    condition     = can(jsondecode(var.policy_json).Statement)
    error_message = "policy_json must be an IAM policy document."
  }
}

variable "aliases" {
  description = "Aliases that point at the version published by this change, for example dev. Callers start the alias, never a version."
  type        = list(string)
  default     = []

  validation {
    condition     = alltrue([for a in var.aliases : can(regex("^[A-Za-z0-9_-]{1,80}$", a))])
    error_message = "An alias name is letters, digits, hyphens and underscores."
  }
}

variable "log_retention_days" {
  description = "How long execution logs are kept. Logs hold no request data."
  type        = number
  default     = 90

  validation {
    condition     = contains([1, 3, 5, 7, 14, 30, 60, 90, 120, 150, 180, 365, 400, 545, 731, 1096], var.log_retention_days)
    error_message = "log_retention_days must be a value CloudWatch Logs accepts."
  }
}

variable "alarm_topic_arns" {
  description = "Topics told when an execution fails, times out or is aborted, and when it is fine again."
  type        = list(string)
  default     = []
}
