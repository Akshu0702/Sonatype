mock_provider "aws" {}

variables {
  identifier             = "mls-rds-prod-db-001"
  engine_version         = "16.4"
  parameter_group_family = "postgres16"
  kms_key_id             = "arn:aws:kms:ap-south-1:111111111111:key/11111111-1111-1111-1111-111111111111"
  vpc_id                 = "vpc-0aaaaaaaaaaaaaaa1"
  subnet_ids             = ["subnet-0aaaaaaaaaaaaaaa1", "subnet-0aaaaaaaaaaaaaaa2", "subnet-0aaaaaaaaaaaaaaa3"]

  allowed_security_group_ids = ["sg-0aaaaaaaaaaaaaaa1"]
  allowed_cidrs              = ["10.40.0.0/20"]
}

run "secure_defaults" {
  command = plan

  assert {
    condition     = aws_db_instance.this.storage_encrypted == true && aws_db_instance.this.publicly_accessible == false && aws_db_instance.this.deletion_protection == true
    error_message = "Encrypted, private and protected from deletion by default."
  }

  assert {
    condition     = aws_db_instance.this.multi_az == true && aws_db_instance.this.backup_retention_period == 30 && aws_db_instance.this.skip_final_snapshot == false
    error_message = "Multi-AZ, 30 days of backups and a final snapshot by default."
  }
}

run "no_password_in_terraform" {
  command = plan

  assert {
    condition     = aws_db_instance.this.manage_master_user_password == true && aws_db_instance.this.master_user_secret_kms_key_id == "arn:aws:kms:ap-south-1:111111111111:key/11111111-1111-1111-1111-111111111111"
    error_message = "RDS must manage the master password in Secrets Manager with our key."
  }
}

run "encrypted_connections_only_and_connection_logging" {
  command = plan

  assert {
    condition = (
      anytrue([for p in aws_db_parameter_group.this.parameter : p.name == "rds.force_ssl" && p.value == "1"]) &&
      anytrue([for p in aws_db_parameter_group.this.parameter : p.name == "log_connections" && p.value == "1"]) &&
      anytrue([for p in aws_db_parameter_group.this.parameter : p.name == "log_disconnections" && p.value == "1"])
    )
    error_message = "force_ssl, log_connections and log_disconnections must be set."
  }
}

run "access_is_limited_to_the_listed_sources" {
  command = plan

  assert {
    condition     = length(aws_vpc_security_group_ingress_rule.from_security_group) == 1 && length(aws_vpc_security_group_ingress_rule.from_cidr) == 1
    error_message = "One rule per allowed security group and one per allowed range, and nothing else."
  }
}

run "extra_parameters_are_added" {
  command = plan

  variables {
    extra_parameters = [{ name = "log_min_duration_statement", value = "1000" }]
  }

  assert {
    condition     = length(aws_db_parameter_group.this.parameter) == 4
    error_message = "The three built-in parameters plus one extra."
  }
}

run "logs_go_to_cloudwatch_and_insights_uses_our_key" {
  command = plan

  assert {
    condition     = aws_db_instance.this.enabled_cloudwatch_logs_exports == toset(["postgresql", "upgrade"]) && aws_db_instance.this.performance_insights_enabled == true
    error_message = "PostgreSQL and upgrade logs are exported, and Performance Insights is on."
  }
}

run "rejects_a_short_backup_retention" {
  command = plan

  variables {
    backup_retention_period = 3
  }

  expect_failures = [var.backup_retention_period]
}

run "rejects_a_single_subnet" {
  command = plan

  variables {
    subnet_ids = ["subnet-0aaaaaaaaaaaaaaa1"]
  }

  expect_failures = [var.subnet_ids]
}

run "rejects_a_bad_identifier" {
  command = plan

  variables {
    identifier = "Bad_Identifier"
  }

  expect_failures = [var.identifier]
}
