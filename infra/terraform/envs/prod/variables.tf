variable "region" {
  description = "AWS region."
  type        = string
  default     = "us-east-1"
}

variable "name_prefix" {
  description = "Prefix for every resource name."
  type        = string
  default     = "prod-ticketing-"
}

variable "container_image" {
  description = "Image to deploy: <ecr_repository_url>:<version> (outputs of the bootstrap stack and the release)."
  type        = string
}

variable "admin_key_parameter_name" {
  description = "Name of the SecureString SSM parameter with the admin key, created with the AWS CLI before the first apply."
  type        = string
  default     = "/prod-ticketing/admin-api-key"
}

variable "alert_email" {
  description = "Email that receives the alarms."
  type        = string
}

variable "desired_count" {
  description = "Tasks of the service. 0 stops the application without destroying the infrastructure."
  type        = number
  default     = 1
}

variable "deletion_protection" {
  description = "Protects the tables and the user pool from deletion. Set false only to destroy the environment."
  type        = bool
  default     = true
}

variable "throttling_rate_limit" {
  description = "Steady-state requests per second that the API accepts."
  type        = number
  default     = 10
}

variable "throttling_burst_limit" {
  description = "Requests that the API accepts in a burst."
  type        = number
  default     = 20
}
