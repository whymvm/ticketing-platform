output "events_table_name" {
  description = "Name of the events table."
  value       = aws_dynamodb_table.events.name
}

output "events_table_arn" {
  description = "ARN of the events table (for IAM policies)."
  value       = aws_dynamodb_table.events.arn
}

output "orders_table_name" {
  description = "Name of the orders table."
  value       = aws_dynamodb_table.orders.name
}

output "orders_table_arn" {
  description = "ARN of the orders table (for IAM policies)."
  value       = aws_dynamodb_table.orders.arn
}
