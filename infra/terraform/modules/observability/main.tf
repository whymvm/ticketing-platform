# A few alarms on free AWS metrics, all sent to one email. The first 10 CloudWatch alarms are free.
# The topic has no KMS key: CloudWatch alarms cannot publish to a topic encrypted with the AWS managed key, and the
# alerts carry no sensitive data.

resource "aws_sns_topic" "alerts" {
  # checkov:skip=CKV_AWS_26:CloudWatch alarms cannot use the AWS managed SNS key; the alerts carry no sensitive data
  name = "${var.name_prefix}alerts"
}

resource "aws_sns_topic_subscription" "email" {
  topic_arn = aws_sns_topic.alerts.arn
  protocol  = "email"
  endpoint  = var.alert_email
}

locals {
  alarms = {
    # Possible abuse or attack: requests far above what one evaluator sends
    api-requests = {
      namespace   = "AWS/ApiGateway"
      metric      = "Count"
      dimensions  = { ApiId = var.api_id }
      statistic   = "Sum"
      threshold   = var.requests_alarm_threshold
      description = "Unusual number of requests: possible abuse. Stop the service if it is not expected (see the runbook)."
    }
    api-5xx = {
      namespace   = "AWS/ApiGateway"
      metric      = "5xx"
      dimensions  = { ApiId = var.api_id }
      statistic   = "Sum"
      threshold   = 5
      description = "The API answers with server errors."
    }
    # A message that failed 5 times: an order that could not be processed
    dlq-messages = {
      namespace   = "AWS/SQS"
      metric      = "ApproximateNumberOfMessagesVisible"
      dimensions  = { QueueName = var.dlq_name }
      statistic   = "Maximum"
      threshold   = 0
      description = "Messages in the dead-letter queue: orders that could not be processed."
    }
    # The consumer is not keeping up, or it stopped
    queue-age = {
      namespace   = "AWS/SQS"
      metric      = "ApproximateAgeOfOldestMessage"
      dimensions  = { QueueName = var.queue_name }
      statistic   = "Maximum"
      threshold   = 300
      description = "The oldest reservation waits more than 5 minutes: the consumer is slow or stopped."
    }
    service-cpu = {
      namespace   = "AWS/ECS"
      metric      = "CPUUtilization"
      dimensions  = { ClusterName = var.cluster_name, ServiceName = var.service_name }
      statistic   = "Average"
      threshold   = 85
      description = "The service is at its CPU limit."
    }
  }
}

resource "aws_cloudwatch_metric_alarm" "this" {
  for_each = local.alarms

  alarm_name          = "${var.name_prefix}${each.key}"
  alarm_description   = each.value.description
  namespace           = each.value.namespace
  metric_name         = each.value.metric
  dimensions          = each.value.dimensions
  statistic           = each.value.statistic
  period              = 300
  evaluation_periods  = 1
  comparison_operator = "GreaterThanThreshold"
  threshold           = each.value.threshold
  treat_missing_data  = "notBreaching"
  alarm_actions       = [aws_sns_topic.alerts.arn]
  ok_actions          = [aws_sns_topic.alerts.arn]
}
