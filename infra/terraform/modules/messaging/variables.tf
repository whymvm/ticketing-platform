variable "name_prefix" {
  description = "Prefix for every queue name, e.g. \"local-ticketing-\"."
  type        = string
}

variable "max_receive_count" {
  description = "Receives before a message is moved to its dead-letter queue."
  type        = number
  default     = 5
}
