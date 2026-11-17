# prod: Production on Kubernetes. Three zones, one NAT gateway per zone, a Multi-AZ database.
#
# NOTHING FOR THIS ENVIRONMENT EXISTS IN AWS. This stack is code only and is inert: `enabled` is false, so a plan or an apply
# creates nothing. Turning it on is a separate, approved decision (see README.md); it must not be done from a pipeline.
terraform {
  required_version = ">= 1.9.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }

  # Partial configuration: pass -backend-config=backend.hcl (see backend.hcl.example). The state key is separate per environment.
  backend "s3" {}
}

provider "aws" {
  region              = var.region
  allowed_account_ids = [var.account_id]
}

# The guard: turning the stack on needs the exact confirmation text, so it cannot happen by a default or a copy-paste.
resource "terraform_data" "guard" {
  input = var.enabled

  lifecycle {
    precondition {
      condition     = !var.enabled || var.confirm == "create-prod-in-aws"
      error_message = "enabled is true but confirm is not \"create-prod-in-aws\". Read README.md before turning this stack on."
    }

    precondition {
      condition     = !var.enabled || var.admin_role_arn != null
      error_message = "admin_role_arn is required when the stack is enabled: the role that administers the cluster."
    }
  }
}

module "environment" {
  source = "../../modules/k8s-environment"
  count  = var.enabled ? 1 : 0

  environment        = "prod"
  region             = var.region
  cluster_mode       = var.cluster_mode
  shared_cluster     = var.shared_cluster
  cidr_block         = var.cidr_block
  azs                = var.azs
  kubernetes_version = var.kubernetes_version

  access_entries = {
    platform_admin = { principal_arn = coalesce(var.admin_role_arn, "arn:aws:iam::${var.account_id}:role/not-set") }
  }

  node_groups = var.node_groups
  db          = var.db
}
