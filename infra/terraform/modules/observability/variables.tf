variable "name_prefix" {
  description = "Prefix for every resource name."
  type        = string
}

variable "alert_email" {
  description = "Email that receives the alarms. AWS sends a confirmation message first."
  type        = string
}

variable "api_id" {
  description = "HTTP API ID."
  type        = string
}

variable "cluster_name" {
  description = "ECS cluster name."
  type        = string
}

variable "service_name" {
  description = "ECS service name."
  type        = string
}

variable "queue_name" {
  description = "Reservations queue name."
  type        = string
}

variable "dlq_name" {
  description = "Dead-letter queue of the reservations."
  type        = string
}

variable "requests_alarm_threshold" {
  description = "Requests in 5 minutes that suggest abuse of the API."
  type        = number
  default     = 2000
}
