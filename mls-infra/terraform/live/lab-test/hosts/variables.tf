variable "region" {
  description = "The only region used. Everything already in the account lives elsewhere."
  type        = string
  default     = "ap-south-1"

  validation {
    condition     = var.region == "ap-south-1"
    error_message = "Only ap-south-1 (Mumbai) is allowed."
  }
}

variable "account_id" {
  description = "The only account this may run against. The provider refuses any other."
  type        = string
  default     = "096631344162"

  validation {
    condition     = can(regex("^[0-9]{12}$", var.account_id))
    error_message = "account_id must be a 12-digit account number."
  }
}

variable "agent_instance_type" {
  description = "Build agent size: Maven, Docker and Terraform builds. A burstable t-series type keeps the cost down for work that comes in bursts."
  type        = string
  default     = "t3.xlarge"
}

variable "agent_cpu_credits" {
  description = "standard: when the CPU credits run out the agent slows to its baseline and never costs more than the hourly price. unlimited: full speed, extra use is billed."
  type        = string
  default     = "standard"

  validation {
    condition     = contains(["standard", "unlimited"], var.agent_cpu_credits)
    error_message = "agent_cpu_credits must be standard or unlimited."
  }
}

variable "app_instance_type" {
  description = "Application host size: PostgreSQL, Kafka, Keycloak and the services together."
  type        = string
  default     = "m6i.2xlarge"
}

variable "start_cron" {
  description = "When both instances start (India time): weekdays 10:30, half an hour before delivery hours begin."
  type        = string
  default     = "cron(30 10 ? * MON-FRI *)"
}

variable "stop_cron" {
  description = "When both instances stop (India time): weekdays 19:30, half an hour after delivery hours end."
  type        = string
  default     = "cron(30 19 ? * MON-FRI *)"
}

variable "agent_version" {
  description = "Azure Pipelines agent release to install (not register)."
  type        = string
  default     = "5.281.1"
}

variable "agent_sha256" {
  description = "SHA-256 of vsts-agent-linux-x64-<version>.tar.gz from the release notes; the boot script refuses a download that does not match."
  type        = string
  default     = "c9579f76fd71a013e3b9010155454e20592d935134e4fcd04adc5283a820167e"

  validation {
    condition     = can(regex("^[0-9a-f]{64}$", var.agent_sha256))
    error_message = "agent_sha256 must be 64 lower-case hex characters."
  }
}
