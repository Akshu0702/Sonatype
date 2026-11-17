output "topic_arns" {
  description = "SNS topic ARNs by severity, for the router function subscriptions."
  value       = { for k, t in aws_sns_topic.severity : k => t.arn }
}

output "alarm_arns" {
  description = "Alarm ARNs by alert id."
  value       = { for k, a in aws_cloudwatch_metric_alarm.this : k => a.arn }
}
