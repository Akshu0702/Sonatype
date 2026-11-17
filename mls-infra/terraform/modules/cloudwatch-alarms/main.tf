# One SNS topic per severity. A router function subscribes to each topic and
# opens the portal incident (see the wiki: Monitoring and Alerting).
resource "aws_sns_topic" "severity" {
  for_each = var.topic_ids

  name              = "${var.name_prefix}-${each.value}"
  kms_master_key_id = var.kms_key_id

  tags = merge(var.tags, {
    severity = upper(each.key)
  })
}

resource "aws_cloudwatch_metric_alarm" "this" {
  for_each = var.alarms

  alarm_name          = each.key
  alarm_description   = each.value.description
  namespace           = each.value.namespace
  metric_name         = each.value.metric_name
  statistic           = each.value.statistic
  period              = each.value.period
  evaluation_periods  = each.value.evaluation_periods
  threshold           = each.value.threshold
  comparison_operator = each.value.comparison_operator
  treat_missing_data  = each.value.treat_missing_data
  dimensions          = each.value.dimensions

  alarm_actions = [aws_sns_topic.severity[each.value.severity].arn]
  ok_actions    = [aws_sns_topic.severity[each.value.severity].arn]

  tags = merge(var.tags, {
    severity = upper(each.value.severity)
    owner    = each.value.owner
    runbook  = each.value.runbook_url
  })
}
