variable "region" {
  description = "AWS region emulated by LocalStack."
  type        = string
  default     = "us-east-1"
}

variable "localstack_endpoint" {
  description = "LocalStack edge endpoint reachable from the Terraform container."
  type        = string
  default     = "http://localstack:4566"
}

variable "name_prefix" {
  description = "Prefix for every resource name."
  type        = string
  default     = "local-ticketing-"
}
