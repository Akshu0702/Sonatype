variable "environment" {
  description = "Where this runs, from the naming standard."
  type        = string
  default     = "dev"
}

variable "owner" {
  description = "The team that owns the platform resources (never a person)."
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
  description = "The account environments are launched in."
  type        = string

  validation {
    condition     = can(regex("^[0-9]{12}$", var.account_id))
    error_message = "account_id must be a 12-digit account number."
  }
}

variable "tags" {
  description = "Tags for the platform resources (not for the environments: the state machine tags those)."
  type        = map(string)
  default     = {}
}

# ---------------------------------------------------------------- where environments run
variable "vpc_id" {
  description = "VPC the environments run in."
  type        = string
}

variable "subnet_ids" {
  description = "Subnets an environment may be launched into, one is picked at random. Private subnets, with a way to reach Systems Manager (endpoints or NAT)."
  type        = list(string)

  validation {
    condition     = length(var.subnet_ids) >= 1 && alltrue([for s in var.subnet_ids : can(regex("^subnet-[0-9a-f]{8,17}$", s))])
    error_message = "subnet_ids needs at least one subnet id."
  }
}

variable "associate_public_ip" {
  description = "Give an environment a public address for outbound traffic. Off: use private subnets with endpoints or NAT. On is only for a VPC that has public subnets and no NAT, like the first lab network: the security group still allows nothing in from the internet."
  type        = bool
  default     = false
}

variable "create_ssm_endpoints" {
  description = "Create the three Systems Manager interface endpoints (ssm, ssmmessages, ec2messages) in the first subnet, so private instances can be reached without NAT. They cost money by the hour whether or not an environment exists."
  type        = bool
  default     = false
}

variable "egress_tcp_ports" {
  description = "Outbound TCP ports of an environment. Web traffic only by default."
  type        = list(number)
  default     = [80, 443]
}

variable "access_security_group_ids" {
  description = "Security groups (for example the build agent's) that may reach the environments on access_ports. None by default: nothing can connect in."
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

  validation {
    condition     = length(var.access_security_group_ids) == 0 || var.access_ports != null
    error_message = "access_ports is required when access_security_group_ids is set."
  }
}

# ---------------------------------------------------------------- what can be asked for
variable "configurations" {
  description = <<-EOT
    The catalogue of environments a request may name (the request says `configuration: <key>`). An entry with no ami_id uses the
    default image below. postgres_package installs that PostgreSQL package at first boot (only postgresql-16 comes with Ubuntu 24.04).
  EOT
  type = map(object({
    description      = optional(string, "")
    ami_id           = optional(string)
    instance_type    = optional(string, "t3.small")
    root_volume_gb   = optional(number, 30)
    postgres_package = optional(string)
  }))
  default = {}

  validation {
    condition     = alltrue([for k, c in var.configurations : can(regex("^[a-z0-9][a-z0-9-]{1,40}$", k))])
    error_message = "A configuration key is lower case letters, digits and hyphens, 2 to 41 characters."
  }

  validation {
    condition     = alltrue([for k, c in var.configurations : contains(var.allowed_instance_types, c.instance_type)])
    error_message = "A configuration may only use an instance type from allowed_instance_types."
  }

  validation {
    condition     = alltrue([for k, c in var.configurations : c.root_volume_gb >= 20 && c.root_volume_gb <= 200])
    error_message = "root_volume_gb must be from 20 to 200."
  }

  validation {
    condition     = alltrue([for k, c in var.configurations : c.ami_id == null || can(regex("^ami-[0-9a-f]{8,17}$", coalesce(c.ami_id, "x")))])
    error_message = "ami_id must look like ami-0123456789abcdef0."
  }

  validation {
    condition     = alltrue([for k, c in var.configurations : c.postgres_package == null || coalesce(c.postgres_package, "x") == "postgresql-16"])
    error_message = "Only postgresql-16 is supported at first boot: other versions need a package repository that is not set up."
  }
}

variable "allowed_instance_types" {
  description = "The only instance types a request can end up with. Burstable types, run with standard credits, so the cost of one environment is capped by its hourly price."
  type        = list(string)
  default     = ["t3.small", "t3.medium", "t3.large"]

  validation {
    condition     = alltrue([for t in var.allowed_instance_types : can(regex("^t[0-9][a-z]*\\.[a-z0-9]+$", t))])
    error_message = "Only burstable (t-series) types: the launch asks for standard credits, which other families do not accept."
  }
}

variable "ami_id" {
  description = "Image for configurations with no ami_id of their own. The hardened image. Null: look the image up by hardened_ami_name_prefix, and if that is null too, fall back to the newest Canonical Ubuntu 24.04 (NOT hardened: a fallback for trying the flow)."
  type        = string
  default     = null

  validation {
    condition     = var.ami_id == null || can(regex("^ami-[0-9a-f]{8,17}$", coalesce(var.ami_id, "x")))
    error_message = "ami_id must look like ami-0123456789abcdef0."
  }
}

variable "hardened_ami_name_prefix" {
  description = "Name prefix of the images the image-builder module produces (in this account). The newest one is used."
  type        = string
  default     = null
}

# ---------------------------------------------------------------- lifetime
variable "default_ttl_hours" {
  description = "Life of an environment when the request names none."
  type        = number
  default     = 8

  validation {
    condition     = var.default_ttl_hours >= 1 && var.default_ttl_hours <= var.max_ttl_hours && var.default_ttl_hours == floor(var.default_ttl_hours)
    error_message = "default_ttl_hours must be a whole number from 1 to max_ttl_hours."
  }
}

variable "max_ttl_hours" {
  description = "The longest life of an environment. A longer one needs a new ticket."
  type        = number
  default     = 72

  validation {
    condition     = var.max_ttl_hours >= 1 && var.max_ttl_hours <= 72 && var.max_ttl_hours == floor(var.max_ttl_hours)
    error_message = "max_ttl_hours is a whole number up to 72 (the execution time limit is set for that)."
  }
}

variable "reaper_enabled" {
  description = "Run the ttl-reaper on its timetable."
  type        = bool
  default     = true
}

variable "reaper_schedule" {
  description = "How often the ttl-reaper looks for expired environments."
  type        = string
  default     = "rate(15 minutes)"

  validation {
    condition     = startswith(var.reaper_schedule, "rate(") || startswith(var.reaper_schedule, "cron(")
    error_message = "reaper_schedule must be a rate(...) or cron(...) expression."
  }
}

# ---------------------------------------------------------------- the state machines
variable "provision_definition" {
  description = "The provision-environment state machine as a template (mls-automation-api/statemachines/provision-environment.asl.json)."
  type        = string
}

variable "reaper_definition" {
  description = "The ttl-reaper state machine as a template (mls-automation-api/statemachines/ttl-reaper.asl.json)."
  type        = string
}

variable "alarm_topic_arns" {
  description = "Topics told when an execution of either state machine fails."
  type        = list(string)
  default     = []
}
