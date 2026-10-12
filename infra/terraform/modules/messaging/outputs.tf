output "queue_urls" {
  description = "Main queue URLs by responsibility (reservations, payments)."
  value       = { for name, queue in aws_sqs_queue.main : name => queue.id }
}

output "dlq_urls" {
  description = "Dead-letter queue URLs by responsibility."
  value       = { for name, queue in aws_sqs_queue.dlq : name => queue.id }
}

output "queue_arns" {
  description = "Main queue ARNs."
  value       = { for name, queue in aws_sqs_queue.main : name => queue.arn }
}

output "queue_names" {
  description = "Main queue names."
  value       = { for name, queue in aws_sqs_queue.main : name => queue.name }
}

output "dlq_names" {
  description = "Dead-letter queue names."
  value       = { for name, queue in aws_sqs_queue.dlq : name => queue.name }
}
