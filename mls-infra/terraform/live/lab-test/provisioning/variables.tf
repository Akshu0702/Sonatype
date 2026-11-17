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

# ---------------------------------------------------------------- network (from the hosts stack: terraform output vpc_id, and its subnets)
variable "vpc_id" {
  description = "The lab VPC (mls-vpc-dev-mgmt-001). Never the default VPC."
  type        = string
}

variable "subnet_ids" {
  description = "Subnets environments are launched into (and the image build runs in the first, which needs outbound access: see the image-builder module). The lab VPC has two public subnets today and no private ones."
  type        = list(string)
}

variable "associate_public_ip" {
  description = "Outbound-only public address for environments (not for the image build). Needed while the VPC has only public subnets and no NAT, exactly as for the existing hosts. Nothing is allowed in from the internet either way. Turn it off once private subnets with endpoints or NAT exist."
  type        = bool
  default     = false
}

variable "create_ssm_endpoints" {
  description = "Create Systems Manager endpoints so private instances need no public address or NAT. They bill by the hour."
  type        = bool
  default     = false
}

variable "access_security_group_ids" {
  description = "Groups that may reach the environments (for example the build agent's, for tests)."
  type        = list(string)
  default     = []
}

variable "access_ports" {
  description = "Ports reachable from access_security_group_ids."
  type = object({
    from_port = number
    to_port   = number
  })
  default = null
}

# ---------------------------------------------------------------- what can be asked for
variable "configurations" {
  description = "The catalogue of environments a request may name."
  type = map(object({
    description      = optional(string, "")
    ami_id           = optional(string)
    instance_type    = optional(string, "t3.small")
    root_volume_gb   = optional(number, 30)
    postgres_package = optional(string)
  }))
  default = {
    "ubuntu24-pg16" = {
      description      = "Hardened Ubuntu 24.04 LTS with PostgreSQL 16"
      postgres_package = "postgresql-16"
    }
    "ubuntu24-plain" = {
      description = "Hardened Ubuntu 24.04 LTS, no database"
    }
  }
}

variable "ami_id" {
  description = "Image for the configurations. Null: the newest image with hardened_ami_name_prefix, or the plain Ubuntu fallback when that is null too."
  type        = string
  default     = null
}

variable "hardened_ami_name_prefix" {
  description = "Set to mls-hardened-ubuntu-24 once the first image has been built. Null until then, so environments use the NOT hardened Ubuntu fallback (the output uses_fallback_image says so)."
  type        = string
  default     = null
}

variable "default_ttl_hours" {
  description = "Life of an environment when the request names none."
  type        = number
  default     = 8
}

variable "max_ttl_hours" {
  description = "The longest life of an environment."
  type        = number
  default     = 72
}

variable "reaper_enabled" {
  description = "Run the ttl-reaper every 15 minutes."
  type        = bool
  default     = true
}

# ---------------------------------------------------------------- the image build
variable "enable_image_builder" {
  description = "Create the pipeline that builds the hardened image."
  type        = bool
  default     = true
}

variable "artifact_bucket" {
  description = "The bucket that holds the hardening archive under images/."
  type        = string
  default     = "mls-deploy-artifacts-096631344162"
}

variable "hardening_archive_key" {
  description = "Archive of the ansible/ folder of this repository, uploaded before the first build."
  type        = string
  default     = "images/ansible-1.0.0.tar.gz"
}

variable "scan_images" {
  description = "Scan each new image with Amazon Inspector (Inspector must be enabled once, see iam-by-hand.md)."
  type        = bool
  default     = true
}

variable "image_schedule" {
  description = "A cron(...) expression to rebuild on a timetable. Null: build by hand."
  type        = string
  default     = null
}

# ---------------------------------------------------------------- the state machines
variable "definitions_dir" {
  description = "Folder with provision-environment.asl.json and ttl-reaper.asl.json: statemachines/ of mls-automation-api, checked out next to this repository."
  type        = string
  default     = "../../../../../mls-automation-api/statemachines"
}

variable "alert_email" {
  description = "Address told when an execution fails. The first message must be confirmed by its recipient. Null: the topic exists and nobody is subscribed."
  type        = string
  default     = null
}
