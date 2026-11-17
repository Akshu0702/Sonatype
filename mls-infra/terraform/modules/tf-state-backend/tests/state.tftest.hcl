mock_provider "aws" {
  mock_data "aws_iam_policy_document" {
    defaults = {
      json = "{\"Version\":\"2012-10-17\",\"Statement\":[]}"
    }
  }
}

variables {
  bucket_name = "mls-s3-dev-store-001"
}

run "secure_defaults" {
  command = plan

  assert {
    condition     = aws_s3_bucket_public_access_block.state.block_public_acls && aws_s3_bucket_public_access_block.state.block_public_policy && aws_s3_bucket_public_access_block.state.ignore_public_acls && aws_s3_bucket_public_access_block.state.restrict_public_buckets
    error_message = "All four public access blocks must be on."
  }

  assert {
    condition     = aws_s3_bucket_versioning.state.versioning_configuration[0].status == "Enabled"
    error_message = "Versioning must be enabled."
  }

  assert {
    condition     = aws_s3_bucket_ownership_controls.state.rule[0].object_ownership == "BucketOwnerEnforced"
    error_message = "ACLs must be disabled."
  }
}

run "uses_aws_managed_encryption_without_a_key" {
  command = plan

  assert {
    condition     = one([for r in aws_s3_bucket_server_side_encryption_configuration.state.rule : one(r.apply_server_side_encryption_by_default).sse_algorithm]) == "AES256"
    error_message = "Without a key the bucket uses AES256."
  }
}

run "uses_the_customer_key_when_given" {
  command = plan

  variables {
    kms_key_arn = "arn:aws:kms:ap-south-1:111111111111:key/11111111-1111-1111-1111-111111111111"
  }

  assert {
    condition     = one([for r in aws_s3_bucket_server_side_encryption_configuration.state.rule : one(r.apply_server_side_encryption_by_default).sse_algorithm]) == "aws:kms"
    error_message = "With a key the bucket uses aws:kms."
  }
}

run "creates_the_lock_table_by_default" {
  command = plan

  assert {
    condition     = length(aws_dynamodb_table.lock) == 1 && aws_dynamodb_table.lock[0].hash_key == "LockID" && aws_dynamodb_table.lock[0].name == "mls-s3-dev-store-001-lock"
    error_message = "A lock table keyed on LockID is expected."
  }
}

run "can_skip_the_lock_table" {
  command = plan

  variables {
    create_lock_table = false
  }

  assert {
    condition     = length(aws_dynamodb_table.lock) == 0
    error_message = "No lock table when create_lock_table is false."
  }
}
