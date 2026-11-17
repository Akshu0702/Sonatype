data "aws_partition" "current" {}

# Looked up only when no image is pinned. Canonical's account id is fixed.
data "aws_ami" "ubuntu" {
  count       = var.ami_id == null ? 1 : 0
  owners      = ["099720109477"]
  most_recent = true

  filter {
    name   = "name"
    values = ["ubuntu/images/hvm-ssd-gp3/ubuntu-noble-24.04-amd64-server-*"]
  }

  filter {
    name   = "state"
    values = ["available"]
  }
}

locals {
  ami_id = var.ami_id != null ? var.ami_id : data.aws_ami.ubuntu[0].id
}

# ---------------------------------------------------------------- identity
resource "aws_iam_role" "this" {
  name = var.name

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "ec2.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })

  tags = merge(var.tags, { Name = var.name })
}

# Session Manager is the only way in: no key pair, no SSH.
resource "aws_iam_role_policy_attachment" "ssm" {
  role       = aws_iam_role.this.name
  policy_arn = "arn:${data.aws_partition.current.partition}:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

resource "aws_iam_role_policy_attachment" "extra" {
  for_each = toset(var.managed_policy_arns)

  role       = aws_iam_role.this.name
  policy_arn = each.value
}

resource "aws_iam_instance_profile" "this" {
  name = var.name
  role = aws_iam_role.this.name

  tags = merge(var.tags, { Name = var.name })
}

# ---------------------------------------------------------------- network access
resource "aws_security_group" "this" {
  name        = var.name
  description = "Access rules for ${var.name}"
  vpc_id      = var.vpc_id

  tags = merge(var.tags, { Name = var.name })

  lifecycle {
    create_before_destroy = true
  }
}

resource "aws_vpc_security_group_ingress_rule" "this" {
  count = length(var.ingress_rules)

  security_group_id            = aws_security_group.this.id
  description                  = var.ingress_rules[count.index].description
  from_port                    = var.ingress_rules[count.index].from_port
  to_port                      = var.ingress_rules[count.index].to_port
  ip_protocol                  = var.ingress_rules[count.index].protocol
  referenced_security_group_id = var.ingress_rules[count.index].source_security_group_id
  cidr_ipv4                    = var.ingress_rules[count.index].cidr_ipv4

  tags = var.tags
}

resource "aws_vpc_security_group_egress_rule" "tcp" {
  for_each = toset([for p in var.egress_tcp_ports : tostring(p)])

  security_group_id = aws_security_group.this.id
  description       = "Outbound TCP ${each.value}"
  from_port         = tonumber(each.value)
  to_port           = tonumber(each.value)
  ip_protocol       = "tcp"
  cidr_ipv4         = "0.0.0.0/0"

  tags = var.tags
}

# ---------------------------------------------------------------- the instance
resource "aws_instance" "this" {
  ami                         = local.ami_id
  instance_type               = var.instance_type
  subnet_id                   = var.subnet_id
  vpc_security_group_ids      = [aws_security_group.this.id]
  iam_instance_profile        = aws_iam_instance_profile.this.name
  associate_public_ip_address = var.associate_public_ip
  user_data                   = var.user_data
  monitoring                  = true
  ebs_optimized               = true

  # Stopping from inside the machine stops it; it is never terminated by a shutdown.
  instance_initiated_shutdown_behavior = "stop"

  # Burstable instances only: standard credits cap the cost at the hourly price.
  dynamic "credit_specification" {
    for_each = var.cpu_credits != null && can(regex("^t[0-9][a-z]*\\.", var.instance_type)) ? [1] : []

    content {
      cpu_credits = var.cpu_credits
    }
  }

  # No key pair: sessions go through Session Manager.
  metadata_options {
    http_endpoint               = "enabled"
    http_tokens                 = "required"
    http_put_response_hop_limit = var.imds_hop_limit
    instance_metadata_tags      = "disabled"
  }

  root_block_device {
    volume_type           = "gp3"
    volume_size           = var.root_volume_gb
    encrypted             = true
    kms_key_id            = var.kms_key_arn
    delete_on_termination = true

    tags = merge(var.tags, { Name = var.name })
  }

  tags = merge(var.tags, { Name = var.name })

  lifecycle {
    # A newer image or an edited boot script never replaces a running instance.
    ignore_changes = [ami, user_data]
  }
}

# ---------------------------------------------------------------- start and stop timetable
resource "aws_iam_role" "scheduler" {
  count = var.schedule.enabled ? 1 : 0

  name = "${var.name}-schedule"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "scheduler.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })

  tags = merge(var.tags, { Name = "${var.name}-schedule" })
}

# The timetable may start and stop this one instance and nothing else.
resource "aws_iam_role_policy" "scheduler" {
  count = var.schedule.enabled ? 1 : 0

  name = "start-stop-this-instance"
  role = aws_iam_role.scheduler[0].id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = ["ec2:StartInstances", "ec2:StopInstances"]
      Resource = aws_instance.this.arn
    }]
  })
}

resource "aws_scheduler_schedule" "start" {
  count = var.schedule.enabled ? 1 : 0

  name                         = "${var.name}-start"
  schedule_expression          = var.schedule.start_cron
  schedule_expression_timezone = var.schedule.timezone

  flexible_time_window {
    mode = "OFF"
  }

  target {
    arn      = "arn:${data.aws_partition.current.partition}:scheduler:::aws-sdk:ec2:startInstances"
    role_arn = aws_iam_role.scheduler[0].arn
    input    = jsonencode({ InstanceIds = [aws_instance.this.id] })
  }
}

resource "aws_scheduler_schedule" "stop" {
  count = var.schedule.enabled ? 1 : 0

  name                         = "${var.name}-stop"
  schedule_expression          = var.schedule.stop_cron
  schedule_expression_timezone = var.schedule.timezone

  flexible_time_window {
    mode = "OFF"
  }

  target {
    arn      = "arn:${data.aws_partition.current.partition}:scheduler:::aws-sdk:ec2:stopInstances"
    role_arn = aws_iam_role.scheduler[0].arn
    input    = jsonencode({ InstanceIds = [aws_instance.this.id] })
  }
}
