mock_provider "aws" {
  mock_data "aws_iam_policy_document" {
    defaults = {
      json = "{\"Version\":\"2012-10-17\",\"Statement\":[]}"
    }
  }
}

variables {
  name_prefix = "mls-dev"
  keys = {
    secrets = { description = "Secrets Manager and parameters" }
    s3      = { description = "Object storage", deletion_window_in_days = 14 }
  }
}

run "creates_a_key_and_alias_per_purpose" {
  command = plan

  assert {
    condition     = length(aws_kms_key.this) == 2 && length(aws_kms_alias.this) == 2
    error_message = "One key and one alias per purpose."
  }

  assert {
    condition     = aws_kms_alias.this["secrets"].name == "alias/mls-dev-secrets" && aws_kms_alias.this["s3"].name == "alias/mls-dev-s3"
    error_message = "Aliases must follow alias/<project>-<environment>-<purpose>."
  }
}

run "rotation_is_on_by_default" {
  command = plan

  assert {
    condition     = aws_kms_key.this["secrets"].enable_key_rotation == true && aws_kms_key.this["secrets"].deletion_window_in_days == 30
    error_message = "Rotation on and a 30 day deletion window are the defaults."
  }

  assert {
    condition     = aws_kms_key.this["s3"].deletion_window_in_days == 14
    error_message = "A set deletion window must be kept."
  }
}

run "tags_carry_the_purpose" {
  command = plan

  variables {
    tags = { project = "mls" }
  }

  assert {
    condition     = aws_kms_key.this["s3"].tags["purpose"] == "s3" && aws_kms_key.this["s3"].tags["project"] == "mls"
    error_message = "Keys carry the caller's tags and a purpose tag."
  }
}

run "rejects_a_short_deletion_window" {
  command = plan

  variables {
    keys = {
      bad = { description = "x", deletion_window_in_days = 3 }
    }
  }

  expect_failures = [var.keys]
}

run "rejects_a_bad_key_name" {
  command = plan

  variables {
    keys = {
      "Bad Name" = { description = "x" }
    }
  }

  expect_failures = [var.keys]
}
