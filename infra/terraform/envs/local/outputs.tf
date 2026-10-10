output "events_table_name" {
  description = "Events table name (TABLE_PREFIX + events)."
  value       = module.data.events_table_name
}

output "queue_urls" {
  description = "Main queue URLs."
  value       = module.messaging.queue_urls
}

output "dlq_urls" {
  description = "Dead-letter queue URLs."
  value       = module.messaging.dlq_urls
}
