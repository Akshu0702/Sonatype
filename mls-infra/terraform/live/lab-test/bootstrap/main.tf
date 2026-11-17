# Bootstrap, once: the bucket and lock table that hold the state of every other root module.
# This root module keeps its own state locally (the bucket does not exist yet); nothing else may.
# Run from a workstation under approval; see README.md.
terraform {
  required_version = ">= 1.9.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}

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
}

provider "aws" {
  region              = var.region
  allowed_account_ids = [var.account_id]
}

module "name_state" {
  source = "../../../modules/naming"

  resource_type = "s3"
  environment   = "dev"
  function      = "store"
  owner         = "platform"
  region        = var.region
}

module "state" {
  source = "../../../modules/tf-state-backend"

  bucket_name = module.name_state.name
  tags        = module.name_state.tags
  # AWS-managed encryption until the key module is applied (see the module README).
  kms_key_arn = null
}

output "backend_config" {
  description = "Write this to backend.hcl, add a key per root module, then run terraform init -migrate-state."
  value       = module.state.backend_config
}

output "bucket_id" {
  value = module.state.bucket_id
}
