# On-demand environments: a request starts a Step Functions execution that launches one EC2 instance from the hardened image with
# the mandatory tags (ttl, owner, ticket, expires-at), waits until it expires and terminates it. A second state machine, the
# ttl-reaper, runs every 15 minutes as a safety net. An image pipeline builds the hardened image.
#
#   - the state machine definitions live in mls-automation-api/statemachines (ADR-0009) and are read from definitions_dir;
#   - nothing here is applied yet: see README.md for what was checked and what was not.
terraform {
  required_version = ">= 1.9.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }

  # Partial configuration: pass -backend-config=backend.hcl (see ../README.md).
  backend "s3" {}
}

provider "aws" {
  region              = var.region
  allowed_account_ids = [var.account_id]
}

module "name_alerts" {
  source = "../../../modules/naming"

  resource_type = "sns"
  environment   = "dev"
  function      = "lab"
  owner         = "platform"
  region        = var.region
}

# ---------------------------------------------------------------- who is told
resource "aws_sns_topic" "alerts" {
  name = module.name_alerts.name

  tags = module.name_alerts.tags
}

resource "aws_sns_topic_subscription" "email" {
  count = var.alert_email == null ? 0 : 1

  topic_arn = aws_sns_topic.alerts.arn
  protocol  = "email"
  endpoint  = var.alert_email
}

# ---------------------------------------------------------------- the hardened image
module "image" {
  count  = var.enable_image_builder ? 1 : 0
  source = "../../../modules/image-builder"

  environment           = "dev"
  region                = var.region
  account_id            = var.account_id
  tags                  = module.name_alerts.tags
  vpc_id                = var.vpc_id
  subnet_id             = var.subnet_ids[0]
  artifact_bucket       = var.artifact_bucket
  hardening_archive_key = var.hardening_archive_key
  scan_images           = var.scan_images
  schedule_expression   = var.image_schedule
}

# ---------------------------------------------------------------- the provisioning flow
module "provisioning" {
  source = "../../../modules/provisioning"

  environment = "dev"
  region      = var.region
  account_id  = var.account_id
  tags        = module.name_alerts.tags

  vpc_id               = var.vpc_id
  subnet_ids           = var.subnet_ids
  associate_public_ip  = var.associate_public_ip
  create_ssm_endpoints = var.create_ssm_endpoints

  access_security_group_ids = var.access_security_group_ids
  access_ports              = var.access_ports

  configurations           = var.configurations
  ami_id                   = var.ami_id
  hardened_ami_name_prefix = var.hardened_ami_name_prefix
  default_ttl_hours        = var.default_ttl_hours
  max_ttl_hours            = var.max_ttl_hours
  reaper_enabled           = var.reaper_enabled

  provision_definition = file("${var.definitions_dir}/provision-environment.asl.json")
  reaper_definition    = file("${var.definitions_dir}/ttl-reaper.asl.json")

  alarm_topic_arns = [aws_sns_topic.alerts.arn]
}
