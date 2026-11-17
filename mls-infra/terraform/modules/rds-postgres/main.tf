locals {
  # Keyed by position, not by value: a security group created in the same plan has an id that is only known at apply.
  sg_sources   = { for i, id in var.allowed_security_group_ids : tostring(i) => id }
  cidr_sources = toset(var.allowed_cidrs)
}

resource "aws_db_subnet_group" "this" {
  name       = var.identifier
  subnet_ids = var.subnet_ids

  tags = merge(var.tags, { Name = var.identifier })
}

resource "aws_security_group" "this" {
  name        = "${var.identifier}-db"
  description = "Access to ${var.identifier}"
  vpc_id      = var.vpc_id

  tags = merge(var.tags, { Name = "${var.identifier}-db" })
}

resource "aws_vpc_security_group_ingress_rule" "from_security_group" {
  for_each = local.sg_sources

  security_group_id            = aws_security_group.this.id
  referenced_security_group_id = each.value
  ip_protocol                  = "tcp"
  from_port                    = 5432
  to_port                      = 5432
  description                  = "PostgreSQL from ${each.value}"
}

resource "aws_vpc_security_group_ingress_rule" "from_cidr" {
  for_each = local.cidr_sources

  security_group_id = aws_security_group.this.id
  cidr_ipv4         = each.value
  ip_protocol       = "tcp"
  from_port         = 5432
  to_port           = 5432
  description       = "PostgreSQL from ${each.value}"
}

resource "aws_db_parameter_group" "this" {
  name   = var.identifier
  family = var.parameter_group_family

  # Encrypted connections only, and a record of who connects.
  parameter {
    name         = "rds.force_ssl"
    value        = "1"
    apply_method = "immediate"
  }

  parameter {
    name         = "log_connections"
    value        = "1"
    apply_method = "immediate"
  }

  parameter {
    name         = "log_disconnections"
    value        = "1"
    apply_method = "immediate"
  }

  dynamic "parameter" {
    for_each = { for p in var.extra_parameters : p.name => p }

    content {
      name         = parameter.value.name
      value        = parameter.value.value
      apply_method = parameter.value.apply_method
    }
  }

  tags = merge(var.tags, { Name = var.identifier })
}

resource "aws_db_instance" "this" {
  identifier     = var.identifier
  engine         = "postgres"
  engine_version = var.engine_version
  instance_class = var.instance_class

  allocated_storage     = var.allocated_storage
  max_allocated_storage = var.max_allocated_storage
  storage_type          = var.storage_type
  storage_encrypted     = true
  kms_key_id            = var.kms_key_id

  db_name  = var.db_name
  username = var.username

  # RDS creates the password and keeps it in Secrets Manager, encrypted with our key.
  manage_master_user_password   = true
  master_user_secret_kms_key_id = var.kms_key_id

  multi_az               = var.multi_az
  db_subnet_group_name   = aws_db_subnet_group.this.name
  vpc_security_group_ids = [aws_security_group.this.id]
  parameter_group_name   = aws_db_parameter_group.this.name
  publicly_accessible    = false

  backup_retention_period   = var.backup_retention_period
  backup_window             = var.backup_window
  maintenance_window        = var.maintenance_window
  copy_tags_to_snapshot     = true
  deletion_protection       = var.deletion_protection
  skip_final_snapshot       = false
  final_snapshot_identifier = "${var.identifier}-final"

  auto_minor_version_upgrade      = true
  apply_immediately               = false
  enabled_cloudwatch_logs_exports = ["postgresql", "upgrade"]

  performance_insights_enabled    = var.performance_insights_enabled
  performance_insights_kms_key_id = var.performance_insights_enabled ? var.kms_key_id : null

  tags = merge(var.tags, { Name = var.identifier })
}
