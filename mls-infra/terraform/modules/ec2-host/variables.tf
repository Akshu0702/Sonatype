variable "name" {
  description = "Instance name, from the naming module, for example mls-ec2-dev-mgmt-001. Also names the role, instance profile and security group."
  type        = string
}

variable "tags" {
  description = "Tags from the naming module."
  type        = map(string)
  default     = {}
}

variable "vpc_id" {
  description = "VPC of the instance."
  type        = string
}

variable "subnet_id" {
  description = "Subnet of the instance."
  type        = string
}

variable "instance_type" {
  description = "Instance type, for example m6i.xlarge."
  type        = string
  default     = "m6i.xlarge"

  validation {
    condition     = can(regex("^[a-z][a-z0-9]*[0-9][a-z]*\\.[a-z0-9]+$", var.instance_type))
    error_message = "instance_type must look like m6i.xlarge."
  }
}

variable "ami_id" {
  description = "Image to launch. Null looks up the newest Canonical Ubuntu 24.04 LTS image once; later new images are ignored, so a newer image never replaces a running instance."
  type        = string
  default     = null

  validation {
    condition     = var.ami_id == null || can(regex("^ami-[0-9a-f]{8,17}$", var.ami_id))
    error_message = "ami_id must look like ami-0123456789abcdef0."
  }
}

variable "cpu_credits" {
  description = "Only for burstable (t-series) instances. standard throttles to the baseline when the credits run out and never costs extra; unlimited keeps full speed and bills the extra use. Null leaves the AWS default (unlimited for t3). Ignored for other families."
  type        = string
  default     = null

  validation {
    condition     = var.cpu_credits == null || contains(["standard", "unlimited"], coalesce(var.cpu_credits, "x"))
    error_message = "cpu_credits must be standard or unlimited."
  }
}

variable "root_volume_gb" {
  description = "Size of the encrypted gp3 root volume."
  type        = number
  default     = 100

  validation {
    condition     = var.root_volume_gb >= 30 && var.root_volume_gb <= 2000
    error_message = "root_volume_gb must be from 30 to 2000."
  }
}

variable "kms_key_arn" {
  description = "Customer-managed key for the root volume. Null uses the AWS-managed EBS key."
  type        = string
  default     = null
}

variable "associate_public_ip" {
  description = "Give the instance a public address. It is for outbound access only: the security group has no inbound rule from the internet unless one is added."
  type        = bool
  default     = false
}

variable "imds_hop_limit" {
  description = "Metadata hop limit. 1 keeps containers from reading the instance's credentials; use 2 only on a build agent whose job containers must use its role."
  type        = number
  default     = 1

  validation {
    condition     = contains([1, 2], var.imds_hop_limit)
    error_message = "imds_hop_limit must be 1 or 2."
  }
}

variable "managed_policy_arns" {
  description = "Policies attached to the instance role besides the Session Manager policy."
  type        = list(string)
  default     = []
}

variable "ingress_rules" {
  description = "Inbound rules. Each names exactly one source: a security group or a CIDR range. None by default."
  type = list(object({
    description              = string
    from_port                = number
    to_port                  = number
    protocol                 = optional(string, "tcp")
    source_security_group_id = optional(string)
    cidr_ipv4                = optional(string)
  }))
  default = []

  validation {
    condition     = alltrue([for r in var.ingress_rules : (r.source_security_group_id == null) != (r.cidr_ipv4 == null)])
    error_message = "Each ingress rule needs exactly one of source_security_group_id and cidr_ipv4."
  }

  validation {
    condition     = alltrue([for r in var.ingress_rules : r.cidr_ipv4 != "0.0.0.0/0"])
    error_message = "An ingress rule open to the whole internet (0.0.0.0/0) is not allowed here."
  }
}

variable "egress_tcp_ports" {
  description = "Outbound TCP ports to anywhere. Web traffic only by default: Azure DevOps, package repositories, container registries and AWS endpoints are all HTTPS."
  type        = list(number)
  default     = [80, 443]
}

variable "user_data" {
  description = "Boot script. Changing it does not replace the instance."
  type        = string
  default     = null
}

variable "schedule" {
  description = "Start and stop on a timetable (EventBridge Scheduler). Cron expressions are in the given time zone, for example cron(30 10 ? * MON-FRI *)."
  type = object({
    enabled    = optional(bool, false)
    start_cron = optional(string)
    stop_cron  = optional(string)
    timezone   = optional(string, "Asia/Kolkata")
  })
  default = {}

  validation {
    condition     = !var.schedule.enabled || (var.schedule.start_cron != null && var.schedule.stop_cron != null)
    error_message = "A schedule that is enabled needs both start_cron and stop_cron."
  }

  validation {
    condition     = !var.schedule.enabled || (startswith(coalesce(var.schedule.start_cron, "x"), "cron(") && startswith(coalesce(var.schedule.stop_cron, "x"), "cron("))
    error_message = "start_cron and stop_cron must be cron(...) expressions."
  }
}
