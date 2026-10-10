output "events_table_name" {
  description = "Name of the events table."
  value       = aws_dynamodb_table.events.name
}

output "events_table_arn" {
  description = "ARN of the events table (for IAM policies)."
  value       = aws_dynamodb_table.events.arn
}
