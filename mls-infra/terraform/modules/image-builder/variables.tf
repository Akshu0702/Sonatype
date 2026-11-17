variable "environment" {
  description = "Where this runs, from the naming standard."
  type        = string
  default     = "dev"
}

variable "owner" {
  description = "The team that owns the images (never a person)."
  type        = string
  default     = "platform"
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
  description = "The account the images are built and kept in. Images are never shared with another account."
  type        = string

  validation {
    condition     = can(regex("^[0-9]{12}$", var.account_id))
    error_message = "account_id must be a 12-digit account number."
  }
}

variable "tags" {
  description = "Tags applied to every resource."
  type        = map(string)
  default     = {}
}

variable "vpc_id" {
  description = "VPC the build instance runs in."
  type        = string
}

variable "subnet_id" {
  description = "Subnet of the build instance. The instance must be able to reach package repositories and Systems Manager over HTTPS: Image Builder gives it a public address only if the subnet assigns one on launch, so use a subnet behind NAT or with endpoints, or one that assigns public addresses. The lab VPC today has neither (its subnets do not assign public addresses and there is no NAT), so a build cannot succeed there until that changes."
  type        = string
}

variable "instance_type" {
  description = "Size of the build instance, which runs for the length of one build only."
  type        = string
  default     = "t3.medium"
}

variable "root_volume_gb" {
  description = "Size of the image's root volume (encrypted gp3)."
  type        = number
  default     = 30

  validation {
    condition     = var.root_volume_gb >= 20 && var.root_volume_gb <= 200
    error_message = "root_volume_gb must be from 20 to 200."
  }
}

variable "parent_image" {
  description = "The image to start from. The AWS-managed Ubuntu 24.04 LTS image, newest version."
  type        = string
  default     = "arn:aws:imagebuilder:ap-south-1:aws:image/ubuntu-server-24-lts-x86/x.x.x"
}

variable "artifact_bucket" {
  description = "Bucket that holds the hardening archive, for example mls-deploy-artifacts-096631344162. It must be named mls-*."
  type        = string

  validation {
    condition     = startswith(var.artifact_bucket, "mls-")
    error_message = "artifact_bucket must be named mls-*."
  }
}

variable "hardening_archive_key" {
  description = "Key of the archive of the ansible/ folder of mls-infra, for example images/ansible-1.0.0.tar.gz. The pipeline that builds images uploads it."
  type        = string

  validation {
    condition     = startswith(var.hardening_archive_key, "images/")
    error_message = "The archive lives under images/ so the build role can be limited to that prefix."
  }
}

variable "component_version" {
  description = "Version of the hardening component. A component version cannot be changed once created: change the playbooks, raise this."
  type        = string
  default     = "1.0.0"

  validation {
    condition     = can(regex("^[0-9]+\\.[0-9]+\\.[0-9]+$", var.component_version))
    error_message = "component_version must look like 1.0.0."
  }
}

variable "recipe_version" {
  description = "Version of the image recipe. Raise it with the component version."
  type        = string
  default     = "1.0.0"

  validation {
    condition     = can(regex("^[0-9]+\\.[0-9]+\\.[0-9]+$", var.recipe_version))
    error_message = "recipe_version must look like 1.0.0."
  }
}

variable "ami_name_prefix" {
  description = "Name prefix of the images produced. The provisioning module finds the newest one by this prefix."
  type        = string
  default     = "mls-hardened-ubuntu-24"
}

variable "schedule_expression" {
  description = "A cron(...) expression to rebuild on a timetable (for example monthly, to pick up security updates). Null: build only when someone starts the pipeline."
  type        = string
  default     = null

  validation {
    condition     = var.schedule_expression == null || startswith(coalesce(var.schedule_expression, "x"), "cron(")
    error_message = "schedule_expression must be a cron(...) expression."
  }
}

variable "scan_images" {
  description = "Scan every new image with Amazon Inspector. Inspector must be enabled for the account once (see iam-by-hand.md)."
  type        = bool
  default     = true
}

variable "test_timeout_minutes" {
  description = "How long the component's validate and test phases may take."
  type        = number
  default     = 60

  validation {
    condition     = var.test_timeout_minutes >= 60 && var.test_timeout_minutes <= 1440
    error_message = "Image Builder accepts 60 to 1440 minutes."
  }
}
