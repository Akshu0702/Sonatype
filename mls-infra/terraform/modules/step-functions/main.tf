data "aws_partition" "current" {}

locals {
  state_machine_arn = "arn:${data.aws_partition.current.partition}:states:${var.region}:${var.account_id}:stateMachine:${var.name}"

  # Alarmed on the three ways an execution can end badly. Waiting executions do not count.
  alarmed_metrics = {
    failed    = "ExecutionsFailed"
    timed_out = "ExecutionsTimedOut"
    aborted   = "ExecutionsAborted"
  }
}

# ---------------------------------------------------------------- logs
# The name starts with /aws/vendedlogs/states/ so the service can write to it without a resource policy that grows with every
# state machine. Payloads are never logged (include_execution_data = false): a request may carry a ticket or an owner, and
# logs live longer than executions.
resource "aws_cloudwatch_log_group" "this" {
  name              = "/aws/vendedlogs/states/${var.name}"
  retention_in_days = var.log_retention_days

  tags = var.tags
}

# ---------------------------------------------------------------- the role of this state machine and nothing else
resource "aws_iam_role" "this" {
  name = var.name

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "states.amazonaws.com" }
      Action    = "sts:AssumeRole"
      # Only this state machine, in this account, may use the role (confused deputy).
      Condition = {
        StringEquals = { "aws:SourceAccount" = var.account_id }
        ArnLike      = { "aws:SourceArn" = "${local.state_machine_arn}*" }
      }
    }]
  })

  tags = merge(var.tags, { Name = var.name })
}

resource "aws_iam_role_policy" "tasks" {
  name   = "what-the-tasks-may-do"
  role   = aws_iam_role.this.id
  policy = var.policy_json
}

# What the service needs to deliver execution logs. These actions cannot be limited to one log group.
resource "aws_iam_role_policy" "logging" {
  name = "deliver-execution-logs"
  role = aws_iam_role.this.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect = "Allow"
      Action = [
        "logs:CreateLogDelivery",
        "logs:GetLogDelivery",
        "logs:UpdateLogDelivery",
        "logs:DeleteLogDelivery",
        "logs:ListLogDeliveries",
        "logs:PutResourcePolicy",
        "logs:DescribeResourcePolicies",
        "logs:DescribeLogGroups",
      ]
      Resource = "*"
    }]
  })
}

# ---------------------------------------------------------------- the state machine
resource "aws_sfn_state_machine" "this" {
  name       = var.name
  role_arn   = aws_iam_role.this.arn
  type       = "STANDARD"
  definition = var.definition

  # Every change publishes a numbered version. Callers start an alias, so a release moves the alias and a rollback moves it back.
  publish = true

  logging_configuration {
    log_destination        = "${aws_cloudwatch_log_group.this.arn}:*"
    include_execution_data = false
    level                  = "ERROR"
  }

  tags = merge(var.tags, { Name = var.name })

  depends_on = [aws_iam_role_policy.logging, aws_iam_role_policy.tasks]
}

resource "aws_sfn_alias" "this" {
  for_each = toset(var.aliases)

  name = each.value

  routing_configuration {
    state_machine_version_arn = aws_sfn_state_machine.this.state_machine_version_arn
    weight                    = 100
  }
}

# ---------------------------------------------------------------- alarms
resource "aws_cloudwatch_metric_alarm" "bad_end" {
  for_each = local.alarmed_metrics

  alarm_name          = "${var.name}-${replace(each.key, "_", "-")}"
  alarm_description   = "At least one execution of ${var.name} ended as ${each.value}. Open the execution history in Step Functions; the cause is on the failed state."
  namespace           = "AWS/States"
  metric_name         = each.value
  statistic           = "Sum"
  period              = 300
  evaluation_periods  = 1
  threshold           = 0
  comparison_operator = "GreaterThanThreshold"
  treat_missing_data  = "notBreaching"

  dimensions = {
    StateMachineArn = aws_sfn_state_machine.this.arn
  }

  alarm_actions = var.alarm_topic_arns
  ok_actions    = var.alarm_topic_arns

  tags = var.tags
}
