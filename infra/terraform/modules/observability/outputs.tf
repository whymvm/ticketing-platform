output "alerts_topic_arn" {
  description = "SNS topic of the alarms."
  value       = aws_sns_topic.alerts.arn
}
