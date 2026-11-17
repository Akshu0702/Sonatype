mock_provider "aws" {}

variables {
  environment = "prod"
  secrets = {
    "iam/db-password" = { description = "Portal database credentials", kms_key_id = "alias/mls-prod-secrets" }
    "keycloak/admin"  = { description = "Keycloak bootstrap administrator", kms_key_id = "alias/mls-prod-secrets", recovery_window_in_days = 14 }
    "iq/license"      = { description = "IQ Server licence", kms_key_id = "alias/mls-prod-secrets" }
  }
}

run "names_follow_the_standard" {
  command = plan

  assert {
    condition     = aws_secretsmanager_secret.this["iam/db-password"].name == "mls/prod/iam/db-password" && aws_secretsmanager_secret.this["keycloak/admin"].name == "mls/prod/keycloak/admin"
    error_message = "Names must be <project>/<environment>/<service>/<purpose>."
  }
}

run "kms_key_and_recovery_window" {
  command = plan

  assert {
    condition     = aws_secretsmanager_secret.this["iam/db-password"].kms_key_id == "alias/mls-prod-secrets" && aws_secretsmanager_secret.this["iam/db-password"].recovery_window_in_days == 30 && aws_secretsmanager_secret.this["keycloak/admin"].recovery_window_in_days == 14
    error_message = "Every secret uses a customer key; the recovery window defaults to 30 days."
  }
}

run "no_secret_value_is_ever_set" {
  command = plan

  assert {
    condition     = length(aws_secretsmanager_secret.this) == 3
    error_message = "Only secrets are created. This module has no version resource, so no value is set."
  }
}

run "rotation_replicas_and_policy_are_optional" {
  command = plan

  variables {
    secrets = {
      "iam/db-password" = {
        description         = "Portal database credentials"
        kms_key_id          = "alias/mls-prod-secrets"
        rotation_lambda_arn = "arn:aws:lambda:ap-south-1:111111111111:function:rotate"
        replica_regions     = [{ region = "ap-south-2" }]
        policy              = "{\"Version\":\"2012-10-17\",\"Statement\":[]}"
      }
      "keycloak/admin" = { description = "Keycloak bootstrap administrator", kms_key_id = "alias/mls-prod-secrets" }
    }
  }

  assert {
    condition     = length(aws_secretsmanager_secret_rotation.this) == 1 && length(aws_secretsmanager_secret_policy.this) == 1
    error_message = "Rotation and policy exist only for the secret that asks for them."
  }

  assert {
    condition     = length(aws_secretsmanager_secret.this["iam/db-password"].replica) == 1
    error_message = "One replica region is expected."
  }
}

run "rejects_a_badly_named_secret" {
  command = plan

  variables {
    secrets = {
      "Bad Name" = { description = "x", kms_key_id = "alias/x" }
    }
  }

  expect_failures = [var.secrets]
}

run "rejects_a_short_recovery_window" {
  command = plan

  variables {
    secrets = {
      "iam/x" = { description = "x", kms_key_id = "alias/x", recovery_window_in_days = 3 }
    }
  }

  expect_failures = [var.secrets]
}

run "rejects_an_unknown_environment" {
  command = plan

  variables {
    environment = "staging"
  }

  expect_failures = [var.environment]
}
