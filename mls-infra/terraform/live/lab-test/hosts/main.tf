# The first real environment: a small network with two instances.
#   - a build agent for Azure DevOps (runs CI and the deploy pipelines)
#   - a host for running and testing the application
# Both sit in public subnets WITHOUT any inbound rule from the internet (outbound web traffic only, no NAT gateway),
# are reached through Session Manager, and stop outside delivery hours.
terraform {
  required_version = ">= 1.9.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }

  # Partial configuration: pass -backend-config=backend.hcl (see README.md).
  backend "s3" {}
}

provider "aws" {
  region              = var.region
  allowed_account_ids = [var.account_id]
}

data "aws_availability_zones" "available" {
  state = "available"
}

locals {
  azs = slice(data.aws_availability_zones.available.names, 0, 2)

  # The agent's role carries the same two policies as the mls-deploy user: limited to Mumbai and mls- names,
  # with the guardrails (default VPC protected, no new people or keys). They are created by hand in IAM.
  agent_policy_arns = [
    "arn:aws:iam::${var.account_id}:policy/mls-deploy-allow",
    "arn:aws:iam::${var.account_id}:policy/mls-deploy-guardrails",
  ]

  schedule = {
    enabled    = true
    start_cron = var.start_cron
    stop_cron  = var.stop_cron
    timezone   = "Asia/Kolkata"
  }
}

# ---------------------------------------------------------------- network
module "name_vpc" {
  source = "../../../modules/naming"

  resource_type = "vpc"
  environment   = "dev"
  function      = "mgmt"
  owner         = "platform"
  region        = var.region
}

module "vpc" {
  source = "../../../modules/network-vpc"

  name       = module.name_vpc.name
  cidr_block = "10.40.0.0/20"
  azs        = local.azs
  tags       = module.name_vpc.tags

  # One /24 per zone for the hosts; the rest of the range is left free for the real tiers.
  tiers = {
    hosts = { newbits = 4, netnums = [0, 1], public = true }
  }

  enable_nat                 = false
  enable_s3_gateway_endpoint = false
}

# ---------------------------------------------------------------- build agent
module "name_agent" {
  source = "../../../modules/naming"

  resource_type = "ec2"
  environment   = "dev"
  function      = "mgmt"
  owner         = "platform"
  region        = var.region
}

module "agent" {
  source = "../../../modules/ec2-host"

  name      = module.name_agent.name
  tags      = module.name_agent.tags
  vpc_id    = module.vpc.vpc_id
  subnet_id = module.vpc.subnet_ids["hosts"][0]

  instance_type       = var.agent_instance_type
  cpu_credits         = var.agent_cpu_credits
  root_volume_gb      = 100
  associate_public_ip = true
  # Job containers may use the agent's role (builds run Terraform and the AWS CLI in containers).
  imds_hop_limit      = 2
  managed_policy_arns = local.agent_policy_arns
  schedule            = local.schedule

  user_data = templatefile("${path.module}/agent-bootstrap.sh.tftpl", {
    agent_version = var.agent_version
    agent_sha256  = var.agent_sha256
  })
}

# ---------------------------------------------------------------- application host
module "name_app" {
  source = "../../../modules/naming"

  resource_type = "ec2"
  environment   = "dev"
  function      = "lab"
  owner         = "platform"
  region        = var.region
}

module "app" {
  source = "../../../modules/ec2-host"

  name      = module.name_app.name
  tags      = module.name_app.tags
  vpc_id    = module.vpc.vpc_id
  subnet_id = module.vpc.subnet_ids["hosts"][0]

  instance_type       = var.app_instance_type
  root_volume_gb      = 200
  associate_public_ip = true
  schedule            = local.schedule

  # The agent may reach the application's test ports; nobody else may reach the host at all.
  ingress_rules = [
    {
      description              = "Application and test ports from the build agent"
      from_port                = 8080
      to_port                  = 8089
      source_security_group_id = module.agent.security_group_id
    },
  ]

  user_data = templatefile("${path.module}/app-host-bootstrap.sh.tftpl", {})
}
