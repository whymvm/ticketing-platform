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

output "order_history_table_name" {
  description = "Name of the order history table."
  value       = aws_dynamodb_table.order_history.name
}

output "order_history_table_arn" {
  description = "ARN of the order history table (for IAM policies)."
  value       = aws_dynamodb_table.order_history.arn
}
