output "events_table_name" {
  description = "Events table name (TABLE_PREFIX + events)."
  value       = module.data.events_table_name
}

output "orders_table_name" {
  description = "Orders table name."
  value       = module.data.orders_table_name
}

output "order_history_table_name" {
  description = "Order history table name (TABLE_PREFIX + order-history)."
  value       = module.data.order_history_table_name
}

output "idempotency_table_name" {
  description = "Idempotency table name (TABLE_PREFIX + idempotency)."
  value       = module.data.idempotency_table_name
}

output "queue_urls" {
  description = "Main queue URLs."
  value       = module.messaging.queue_urls
}

output "dlq_urls" {
  description = "Dead-letter queue URLs."
  value       = module.messaging.dlq_urls
}
