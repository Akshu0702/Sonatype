locals {
  rotated  = { for k, s in var.secrets : k => s if s.rotation_lambda_arn != null }
  policies = { for k, s in var.secrets : k => s if s.policy != null }
}

resource "aws_secretsmanager_secret" "this" {
  for_each = var.secrets

  name                    = "${var.project}/${var.environment}/${each.key}"
  description             = each.value.description
  kms_key_id              = each.value.kms_key_id
  recovery_window_in_days = each.value.recovery_window_in_days

  dynamic "replica" {
    for_each = { for r in each.value.replica_regions : r.region => r }

    content {
      region     = replica.value.region
      kms_key_id = replica.value.kms_key_id
    }
  }

  tags = merge(var.tags, {
    Name    = "${var.project}/${var.environment}/${each.key}"
    service = split("/", each.key)[0]
  })
}

resource "aws_secretsmanager_secret_rotation" "this" {
  for_each = local.rotated

  secret_id           = aws_secretsmanager_secret.this[each.key].id
  rotation_lambda_arn = each.value.rotation_lambda_arn

  rotation_rules {
    automatically_after_days = each.value.rotation_days
  }
}

resource "aws_secretsmanager_secret_policy" "this" {
  for_each = local.policies

  secret_arn = aws_secretsmanager_secret.this[each.key].arn
  policy     = each.value.policy
}
