data "aws_partition" "current" {}

module "name" {
  source = "../naming"

  resource_type = "img"
  environment   = var.environment
  function      = "lab"
  owner         = var.owner
  region        = var.region
}

locals {
  base = module.name.name
  tags = merge(var.tags, module.name.tags)
}

# ---------------------------------------------------------------- who the build instance runs as
resource "aws_iam_role" "build" {
  name = "${local.base}-build"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "ec2.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })

  tags = merge(local.tags, { Name = "${local.base}-build" })
}

# The two policies Image Builder documents for its build instance: Session Manager, and what the service itself needs.
resource "aws_iam_role_policy_attachment" "build_ssm" {
  role       = aws_iam_role.build.name
  policy_arn = "arn:${data.aws_partition.current.partition}:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

resource "aws_iam_role_policy_attachment" "build_imagebuilder" {
  role       = aws_iam_role.build.name
  policy_arn = "arn:${data.aws_partition.current.partition}:iam::aws:policy/EC2InstanceProfileForImageBuilder"
}

# The hardening archive, and nothing else in the bucket.
resource "aws_iam_role_policy" "build_archive" {
  name = "read-the-hardening-archive"
  role = aws_iam_role.build.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = "s3:GetObject"
      Resource = "arn:${data.aws_partition.current.partition}:s3:::${var.artifact_bucket}/images/*"
    }]
  })
}

resource "aws_iam_instance_profile" "build" {
  name = "${local.base}-build"
  role = aws_iam_role.build.name

  tags = merge(local.tags, { Name = "${local.base}-build" })
}

resource "aws_security_group" "build" {
  name        = "${local.base}-build"
  description = "Image build instance: web traffic out, nothing in"
  vpc_id      = var.vpc_id

  tags = merge(local.tags, { Name = "${local.base}-build" })

  lifecycle {
    create_before_destroy = true
  }
}

resource "aws_vpc_security_group_egress_rule" "build" {
  for_each = toset(["80", "443"])

  security_group_id = aws_security_group.build.id
  description       = "Outbound TCP ${each.value}"
  from_port         = tonumber(each.value)
  to_port           = tonumber(each.value)
  ip_protocol       = "tcp"
  cidr_ipv4         = "0.0.0.0/0"

  tags = local.tags
}

# ---------------------------------------------------------------- the build
resource "aws_imagebuilder_infrastructure_configuration" "this" {
  name                          = "${local.base}-infra"
  description                   = "Where images are built: one small instance, terminated even when the build fails"
  instance_profile_name         = aws_iam_instance_profile.build.name
  instance_types                = [var.instance_type]
  subnet_id                     = var.subnet_id
  security_group_ids            = [aws_security_group.build.id]
  terminate_instance_on_failure = true

  instance_metadata_options {
    http_tokens                 = "required"
    http_put_response_hop_limit = 1
  }

  # Applied to the build instance and its volumes while a build runs.
  resource_tags = merge(local.tags, { purpose = "image-build" })

  tags = local.tags
}

resource "aws_imagebuilder_component" "harden" {
  name                  = "${local.base}-harden"
  description           = "Applies ansible/playbooks/harden.yml of mls-infra: SSH, kernel network settings, unused modules and packages, time, updates, auditd"
  platform              = "Linux"
  supported_os_versions = ["Ubuntu 24"]
  version               = var.component_version

  data = templatefile("${path.module}/templates/harden-component.yml.tftpl", {
    bucket      = var.artifact_bucket
    archive_key = var.hardening_archive_key
    account_id  = var.account_id
  })

  tags = local.tags
}

resource "aws_imagebuilder_image_recipe" "this" {
  name         = "${local.base}-recipe"
  description  = "Ubuntu 24.04 LTS with the hardening baseline"
  parent_image = var.parent_image
  version      = var.recipe_version

  component {
    component_arn = aws_imagebuilder_component.harden.arn
  }

  block_device_mapping {
    device_name = "/dev/sda1"

    ebs {
      delete_on_termination = true
      encrypted             = true
      volume_size           = var.root_volume_gb
      volume_type           = "gp3"
    }
  }

  # Session Manager is the only way in, so the agent stays on the image.
  systems_manager_agent {
    uninstall_after_build = false
  }

  tags = local.tags
}

# Images stay in this account and this region.
resource "aws_imagebuilder_distribution_configuration" "this" {
  name        = "${local.base}-dist"
  description = "Keep the image in this account and region, named so the newest can be found"

  distribution {
    region = var.region

    ami_distribution_configuration {
      name        = "${var.ami_name_prefix}-{{ imagebuilder:buildDate }}"
      description = "Hardened Ubuntu 24.04 LTS, built by ${local.base}"
      ami_tags    = merge(local.tags, { Name = var.ami_name_prefix, hardened = "true" })
    }
  }

  tags = local.tags
}

resource "aws_imagebuilder_image_pipeline" "this" {
  name                             = "${local.base}-pipeline"
  description                      = "Builds the hardened Ubuntu image. Started by hand or on the timetable."
  image_recipe_arn                 = aws_imagebuilder_image_recipe.this.arn
  infrastructure_configuration_arn = aws_imagebuilder_infrastructure_configuration.this.arn
  distribution_configuration_arn   = aws_imagebuilder_distribution_configuration.this.arn
  status                           = "ENABLED"
  enhanced_image_metadata_enabled  = true

  dynamic "schedule" {
    for_each = var.schedule_expression == null ? [] : [var.schedule_expression]

    content {
      schedule_expression                = schedule.value
      pipeline_execution_start_condition = "EXPRESSION_MATCH_AND_DEPENDENCY_UPDATES_AVAILABLE"
    }
  }

  image_tests_configuration {
    image_tests_enabled = true
    timeout_minutes     = var.test_timeout_minutes
  }

  # Inspector scans each new image; findings are what "scanned before promotion" is judged on.
  image_scanning_configuration {
    image_scanning_enabled = var.scan_images
  }

  tags = local.tags
}
