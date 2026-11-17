data "aws_partition" "current" {}

# ---------------------------------------------------------------- names
module "name_role" {
  source = "../naming"

  resource_type = "role"
  environment   = var.environment
  function      = "lab"
  owner         = var.owner
  region        = var.region
}

module "name_profile" {
  source = "../naming"

  resource_type = "iprof"
  environment   = var.environment
  function      = "lab"
  owner         = var.owner
  region        = var.region
}

module "name_sg" {
  source = "../naming"

  resource_type = "sg"
  environment   = var.environment
  function      = "lab"
  owner         = var.owner
  region        = var.region
}

module "name_provision" {
  source = "../naming"

  resource_type = "sfn"
  environment   = var.environment
  function      = "lab"
  owner         = var.owner
  region        = var.region
  id            = "001"
}

module "name_reaper" {
  source = "../naming"

  resource_type = "sfn"
  environment   = var.environment
  function      = "lab"
  owner         = var.owner
  region        = var.region
  id            = "002"
}

module "name_schedule" {
  source = "../naming"

  resource_type = "sched"
  environment   = var.environment
  function      = "lab"
  owner         = var.owner
  region        = var.region
}

module "name_schedule_role" {
  source = "../naming"

  resource_type = "role"
  environment   = var.environment
  function      = "lab"
  owner         = var.owner
  region        = var.region
  id            = "002"
}

# ---------------------------------------------------------------- the image
# Three ways to name the image, in this order: given, the newest hardened image built here, the plain Ubuntu fallback.
data "aws_ami" "hardened" {
  count       = var.ami_id == null && var.hardened_ami_name_prefix != null ? 1 : 0
  owners      = ["self"]
  most_recent = true

  filter {
    name   = "name"
    values = ["${var.hardened_ami_name_prefix}*"]
  }

  filter {
    name   = "state"
    values = ["available"]
  }
}

data "aws_ami" "ubuntu" {
  count       = var.ami_id == null && var.hardened_ami_name_prefix == null ? 1 : 0
  owners      = ["099720109477"] # Canonical
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
  default_ami = coalesce(
    var.ami_id,
    one(data.aws_ami.hardened[*].id),
    one(data.aws_ami.ubuntu[*].id),
  )

  image_source = var.ami_id != null ? "given" : (var.hardened_ami_name_prefix != null ? "hardened-lookup" : "ubuntu-fallback")

  # What the state machine looks up when a request names a configuration.
  catalogue = {
    for key, c in var.configurations : key => {
      amiId        = coalesce(c.ami_id, local.default_ami)
      instanceType = c.instance_type
      rootVolumeGb = c.root_volume_gb
      userData = templatefile("${path.module}/templates/user-data.sh.tftpl", {
        postgres_package = c.postgres_package == null ? "" : c.postgres_package
      })
    }
  }

  # A value written into a JSONata expression that sits inside a JSON string: encode it twice and drop the outer quotes.
  embed = { for k, v in {
    configurations = local.catalogue
    subnets        = var.subnet_ids
    } : k => substr(jsonencode(jsonencode(v)), 1, length(jsonencode(jsonencode(v))) - 2)
  }

  template_vars = {
    configurations_json   = local.embed.configurations
    subnet_ids_json       = local.embed.subnets
    security_group_id     = aws_security_group.environments.id
    instance_profile_name = aws_iam_instance_profile.environment.name
    associate_public_ip   = tostring(var.associate_public_ip)
    environment           = var.environment
    default_ttl_hours     = tostring(var.default_ttl_hours)
    max_ttl_hours         = tostring(var.max_ttl_hours)
  }

  provision_definition = templatestring(var.provision_definition, local.template_vars)
  reaper_definition    = templatestring(var.reaper_definition, local.template_vars)

  ec2_arn = "arn:${data.aws_partition.current.partition}:ec2:${var.region}:${var.account_id}"
}

# ---------------------------------------------------------------- what an environment runs as
resource "aws_iam_role" "environment" {
  name = module.name_role.name

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "ec2.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })

  tags = merge(var.tags, module.name_role.tags, { Name = module.name_role.name })
}

# Session Manager is the only way in: no key pair, no SSH.
resource "aws_iam_role_policy_attachment" "environment_ssm" {
  role       = aws_iam_role.environment.name
  policy_arn = "arn:${data.aws_partition.current.partition}:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

resource "aws_iam_instance_profile" "environment" {
  name = module.name_profile.name
  role = aws_iam_role.environment.name

  tags = merge(var.tags, module.name_profile.tags, { Name = module.name_profile.name })
}

# ---------------------------------------------------------------- network access of an environment
resource "aws_security_group" "environments" {
  name        = module.name_sg.name
  description = "Provisioned environments: no inbound unless named, web traffic out"
  vpc_id      = var.vpc_id

  tags = merge(var.tags, module.name_sg.tags, { Name = module.name_sg.name })

  lifecycle {
    create_before_destroy = true
  }
}

resource "aws_vpc_security_group_egress_rule" "tcp" {
  for_each = toset([for p in var.egress_tcp_ports : tostring(p)])

  security_group_id = aws_security_group.environments.id
  description       = "Outbound TCP ${each.value}"
  from_port         = tonumber(each.value)
  to_port           = tonumber(each.value)
  ip_protocol       = "tcp"
  cidr_ipv4         = "0.0.0.0/0"

  tags = var.tags
}

resource "aws_vpc_security_group_ingress_rule" "access" {
  for_each = toset(var.access_security_group_ids)

  security_group_id            = aws_security_group.environments.id
  description                  = "Access from ${each.value}"
  from_port                    = var.access_ports.from_port
  to_port                      = var.access_ports.to_port
  ip_protocol                  = "tcp"
  referenced_security_group_id = each.value

  tags = var.tags
}

# Optional: Systems Manager reachable from private subnets without NAT.
resource "aws_security_group" "endpoints" {
  count = var.create_ssm_endpoints ? 1 : 0

  name        = "${module.name_sg.name}-endpoints"
  description = "Systems Manager endpoints: HTTPS from the environments"
  vpc_id      = var.vpc_id

  tags = merge(var.tags, module.name_sg.tags, { Name = "${module.name_sg.name}-endpoints" })

  lifecycle {
    create_before_destroy = true
  }
}

resource "aws_vpc_security_group_ingress_rule" "endpoints" {
  count = var.create_ssm_endpoints ? 1 : 0

  security_group_id            = aws_security_group.endpoints[0].id
  description                  = "HTTPS from the environments"
  from_port                    = 443
  to_port                      = 443
  ip_protocol                  = "tcp"
  referenced_security_group_id = aws_security_group.environments.id

  tags = var.tags
}

resource "aws_vpc_endpoint" "ssm" {
  for_each = var.create_ssm_endpoints ? toset(["ssm", "ssmmessages", "ec2messages"]) : toset([])

  vpc_id              = var.vpc_id
  service_name        = "com.amazonaws.${var.region}.${each.key}"
  vpc_endpoint_type   = "Interface"
  subnet_ids          = [var.subnet_ids[0]]
  security_group_ids  = [aws_security_group.endpoints[0].id]
  private_dns_enabled = true

  tags = merge(var.tags, { Name = "${module.name_sg.name}-${each.key}" })
}

# ---------------------------------------------------------------- permissions
locals {
  ami_arns = [for a in distinct([for k, c in local.catalogue : c.amiId]) : "arn:${data.aws_partition.current.partition}:ec2:${var.region}::image/${a}"]

  # Tags a launch must carry, or it is refused: no tag, no launch.
  required_request_tags = ["owner", "ticket", "expires-at", "ttl", "environment"]

  managed_only = {
    "aws:ResourceTag/managed-by" = "provisioning"
    "aws:ResourceTag/project"    = "mls"
  }

  provision_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid      = "LaunchOnlyTaggedSmallBurstableInstances"
        Effect   = "Allow"
        Action   = "ec2:RunInstances"
        Resource = "${local.ec2_arn}:instance/*"
        Condition = {
          StringEquals = {
            "aws:RequestTag/project"    = "mls"
            "aws:RequestTag/managed-by" = "provisioning"
            "ec2:InstanceType"          = var.allowed_instance_types
            "aws:RequestedRegion"       = var.region
          }
          Null = { for t in local.required_request_tags : "aws:RequestTag/${t}" => "false" }
        }
      },
      {
        Sid    = "LaunchIntoTheNamedSubnetsAndGroupFromTheNamedImages"
        Effect = "Allow"
        Action = "ec2:RunInstances"
        Resource = concat(
          [for s in var.subnet_ids : "${local.ec2_arn}:subnet/${s}"],
          [aws_security_group.environments.arn],
          ["${local.ec2_arn}:network-interface/*"],
          local.ami_arns,
          ["arn:${data.aws_partition.current.partition}:ec2:${var.region}::snapshot/*"],
        )
        Condition = { StringEquals = { "aws:RequestedRegion" = var.region } }
      },
      {
        Sid      = "OnlyEncryptedVolumes"
        Effect   = "Allow"
        Action   = "ec2:RunInstances"
        Resource = "${local.ec2_arn}:volume/*"
        Condition = {
          Bool         = { "ec2:Encrypted" = "true" }
          StringEquals = { "aws:RequestedRegion" = var.region }
        }
      },
      {
        Sid      = "TagWhatTheLaunchCreatesAndNothingElse"
        Effect   = "Allow"
        Action   = "ec2:CreateTags"
        Resource = ["${local.ec2_arn}:instance/*", "${local.ec2_arn}:volume/*", "${local.ec2_arn}:network-interface/*"]
        Condition = {
          StringEquals = { "ec2:CreateAction" = "RunInstances" }
        }
      },
      {
        Sid       = "TerminateOnlyWhatThisPlatformLaunched"
        Effect    = "Allow"
        Action    = "ec2:TerminateInstances"
        Resource  = "${local.ec2_arn}:instance/*"
        Condition = { StringEquals = local.managed_only }
      },
      {
        Sid      = "ReadInstances"
        Effect   = "Allow"
        Action   = "ec2:DescribeInstances"
        Resource = "*"
      },
      {
        Sid       = "HandTheEnvironmentRoleToEc2Only"
        Effect    = "Allow"
        Action    = "iam:PassRole"
        Resource  = aws_iam_role.environment.arn
        Condition = { StringEquals = { "iam:PassedToService" = "ec2.amazonaws.com" } }
      },
      {
        Sid       = "NothingOutsideMumbai"
        Effect    = "Deny"
        Action    = "ec2:*"
        Resource  = "*"
        Condition = { StringNotEquals = { "aws:RequestedRegion" = var.region } }
      },
    ]
  })

  reaper_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid      = "ReadInstances"
        Effect   = "Allow"
        Action   = "ec2:DescribeInstances"
        Resource = "*"
      },
      {
        Sid       = "TerminateOnlyWhatThisPlatformLaunched"
        Effect    = "Allow"
        Action    = "ec2:TerminateInstances"
        Resource  = "${local.ec2_arn}:instance/*"
        Condition = { StringEquals = local.managed_only }
      },
      {
        Sid       = "NothingOutsideMumbai"
        Effect    = "Deny"
        Action    = "ec2:*"
        Resource  = "*"
        Condition = { StringNotEquals = { "aws:RequestedRegion" = var.region } }
      },
    ]
  })
}

# ---------------------------------------------------------------- the two state machines
module "provision" {
  source = "../step-functions"

  name             = module.name_provision.name
  tags             = merge(var.tags, module.name_provision.tags)
  region           = var.region
  account_id       = var.account_id
  definition       = local.provision_definition
  policy_json      = local.provision_policy
  aliases          = [var.environment]
  alarm_topic_arns = var.alarm_topic_arns
}

module "reaper" {
  source = "../step-functions"

  name             = module.name_reaper.name
  tags             = merge(var.tags, module.name_reaper.tags)
  region           = var.region
  account_id       = var.account_id
  definition       = local.reaper_definition
  policy_json      = local.reaper_policy
  aliases          = [var.environment]
  alarm_topic_arns = var.alarm_topic_arns
}

# ---------------------------------------------------------------- the reaper's timetable
resource "aws_iam_role" "scheduler" {
  name = module.name_schedule_role.name

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "scheduler.amazonaws.com" }
      Action    = "sts:AssumeRole"
      Condition = { StringEquals = { "aws:SourceAccount" = var.account_id } }
    }]
  })

  tags = merge(var.tags, module.name_schedule_role.tags, { Name = module.name_schedule_role.name })
}

# The timetable may start the reaper and nothing else.
resource "aws_iam_role_policy" "scheduler" {
  name = "start-the-reaper"
  role = aws_iam_role.scheduler.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = "states:StartExecution"
      Resource = [module.reaper.arn, module.reaper.alias_arns[var.environment]]
    }]
  })
}

resource "aws_scheduler_schedule" "reaper" {
  name                = module.name_schedule.name
  description         = "Starts the ttl-reaper, which terminates environments past their expires-at tag"
  state               = var.reaper_enabled ? "ENABLED" : "DISABLED"
  schedule_expression = var.reaper_schedule

  flexible_time_window {
    mode = "OFF"
  }

  target {
    arn      = "arn:${data.aws_partition.current.partition}:scheduler:::aws-sdk:sfn:startExecution"
    role_arn = aws_iam_role.scheduler.arn
    input    = jsonencode({ StateMachineArn = module.reaper.alias_arns[var.environment], Input = "{}" })

    retry_policy {
      maximum_retry_attempts       = 2
      maximum_event_age_in_seconds = 600
    }
  }
}

# ---------------------------------------------------------------- what the API needs (for the role the API runs as, created with the API)
locals {
  api_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid      = "StartProvisioningAndTheReaper"
        Effect   = "Allow"
        Action   = "states:StartExecution"
        Resource = [module.provision.alias_arns[var.environment], module.reaper.alias_arns[var.environment]]
      },
      {
        Sid    = "ReadExecutions"
        Effect = "Allow"
        Action = ["states:DescribeExecution", "states:ListExecutions", "states:GetExecutionHistory"]
        Resource = [
          "arn:${data.aws_partition.current.partition}:states:${var.region}:${var.account_id}:execution:${module.provision.name}:*",
          module.provision.arn,
        ]
      },
      {
        Sid      = "ReadEnvironments"
        Effect   = "Allow"
        Action   = "ec2:DescribeInstances"
        Resource = "*"
      },
      {
        Sid      = "ExtendOnlyByMovingTheExpiry"
        Effect   = "Allow"
        Action   = "ec2:CreateTags"
        Resource = "${local.ec2_arn}:instance/*"
        Condition = {
          StringEquals                = local.managed_only
          "ForAllValues:StringEquals" = { "aws:TagKeys" = ["expires-at", "ttl"] }
        }
      },
    ]
  })
}
