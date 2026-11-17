mock_provider "aws" {
  mock_data "aws_iam_policy_document" {
    defaults = {
      json = "{\"Version\":\"2012-10-17\",\"Statement\":[]}"
    }
  }
}

variables {
  bucket_name = "mls-s3-prod-store-002"
}

run "secure_defaults" {
  command = plan

  assert {
    condition     = aws_s3_bucket_public_access_block.this.block_public_acls && aws_s3_bucket_public_access_block.this.block_public_policy && aws_s3_bucket_public_access_block.this.ignore_public_acls && aws_s3_bucket_public_access_block.this.restrict_public_buckets
    error_message = "All four public access blocks must be on."
  }

  assert {
    condition     = aws_s3_bucket_versioning.this.versioning_configuration[0].status == "Enabled" && aws_s3_bucket.this.force_destroy == false
    error_message = "Versioning on and force_destroy off are the defaults."
  }

  assert {
    condition     = aws_s3_bucket_ownership_controls.this.rule[0].object_ownership == "BucketOwnerEnforced"
    error_message = "ACLs must be disabled."
  }
}

run "aes256_without_a_key_and_kms_with_one" {
  command = plan

  assert {
    condition     = one([for r in aws_s3_bucket_server_side_encryption_configuration.this.rule : one(r.apply_server_side_encryption_by_default).sse_algorithm]) == "AES256"
    error_message = "Without a key the bucket uses AES256."
  }
}

run "kms_key_is_used_when_given" {
  command = plan

  variables {
    kms_key_arn = "arn:aws:kms:ap-south-1:111111111111:key/11111111-1111-1111-1111-111111111111"
  }

  assert {
    condition     = one([for r in aws_s3_bucket_server_side_encryption_configuration.this.rule : one(r.apply_server_side_encryption_by_default).sse_algorithm]) == "aws:kms"
    error_message = "With a key the bucket uses aws:kms."
  }
}

run "always_cleans_up_incomplete_uploads_and_adds_the_callers_rules" {
  command = plan

  variables {
    lifecycle_rules = [
      { id = "expire-logs", prefix = "logs/", expiration_days = 365, transition_ia_days = 30 },
      { id = "trim-old-versions", noncurrent_expiration_days = 90 },
    ]
  }

  assert {
    condition     = length(aws_s3_bucket_lifecycle_configuration.this.rule) == 3
    error_message = "The built-in multipart rule plus two caller rules."
  }
}

run "replication_and_object_lock_are_off_by_default" {
  command = plan

  assert {
    condition     = length(aws_s3_bucket_replication_configuration.this) == 0 && length(aws_s3_bucket_object_lock_configuration.this) == 0 && aws_s3_bucket.this.object_lock_enabled == false
    error_message = "Neither feature is on unless asked for."
  }
}

run "replication_to_the_dr_bucket" {
  command = plan

  variables {
    kms_key_arn = "arn:aws:kms:ap-south-1:111111111111:key/11111111-1111-1111-1111-111111111111"
    replication = {
      role_arn                = "arn:aws:iam::111111111111:role/mls-role-prod-store-001"
      destination_bucket_arn  = "arn:aws:s3:::mls-s3-prod-store-102"
      destination_kms_key_arn = "arn:aws:kms:ap-south-2:111111111111:key/22222222-2222-2222-2222-222222222222"
    }
  }

  assert {
    condition     = length(aws_s3_bucket_replication_configuration.this) == 1
    error_message = "One replication configuration is expected."
  }
}

run "object_lock_for_audit_exports" {
  command = plan

  variables {
    object_lock = { mode = "COMPLIANCE", days = 365 }
  }

  assert {
    condition     = aws_s3_bucket.this.object_lock_enabled == true && length(aws_s3_bucket_object_lock_configuration.this) == 1
    error_message = "Object lock is enabled on the bucket with a default retention."
  }
}

run "rejects_replication_without_versioning" {
  command = plan

  variables {
    versioning_enabled = false
    replication = {
      role_arn               = "arn:aws:iam::111111111111:role/x"
      destination_bucket_arn = "arn:aws:s3:::y"
    }
  }

  expect_failures = [var.versioning_enabled]
}

run "rejects_a_bad_lock_mode" {
  command = plan

  variables {
    object_lock = { mode = "FOREVER", days = 10 }
  }

  expect_failures = [var.object_lock]
}

run "rejects_a_bad_bucket_name" {
  command = plan

  variables {
    bucket_name = "Bad_Name"
  }

  expect_failures = [var.bucket_name]
}
