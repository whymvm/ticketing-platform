output "queue_urls" {
  description = "Main queue URLs by responsibility (reservations, payments)."
  value       = { for name, queue in aws_sqs_queue.main : name => queue.id }
}

output "dlq_urls" {
  description = "Dead-letter queue URLs by responsibility."
  value       = { for name, queue in aws_sqs_queue.dlq : name => queue.id }
}
