variable "name_prefix" {
  description = "Prefix for the SNS topic names, following the naming standard, for example mls-sns-lab-mgmt. The topic id is appended."
  type        = string
}

variable "topic_ids" {
  description = "Three-digit id appended to the prefix for each severity topic. Ids are never reused."
  type        = map(string)
  default = {
    p1 = "001"
    p2 = "002"
    p3 = "003"
    p4 = "004"
  }
}

variable "kms_key_id" {
  description = "KMS key (id, ARN or alias) used to encrypt the SNS topics. Null uses no customer-managed key."
  type        = string
  default     = null
}

variable "tags" {
  description = "Tags applied to every resource. The calling environment sets project, environment, function, owner and managed-by."
  type        = map(string)
  default     = {}
}

variable "alarms" {
  description = <<-EOT
    Alarms to create, keyed by the alert id from the wiki Alert Catalogue (for example INF-01).
    Every alarm must name an owner and a runbook. An alarm without them is rejected.
  EOT
  type = map(object({
    description         = string
    namespace           = string
    metric_name         = string
    statistic           = optional(string, "Average")
    period              = optional(number, 300)
    evaluation_periods  = optional(number, 1)
    threshold           = number
    comparison_operator = string
    treat_missing_data  = optional(string, "missing")
    dimensions          = optional(map(string), {})
    severity            = string
    owner               = string
    runbook_url         = string
  }))
  default = {}

  validation {
    condition     = alltrue([for k, a in var.alarms : contains(keys(var.topic_ids), a.severity)])
    error_message = "Each alarm severity must be one of the keys of topic_ids (p1, p2, p3, p4)."
  }

  validation {
    condition     = alltrue([for k, a in var.alarms : length(trimspace(a.owner)) > 0 && length(trimspace(a.runbook_url)) > 0])
    error_message = "Every alarm needs a non-empty owner and runbook_url. No owner or no runbook means no alert."
  }
}
