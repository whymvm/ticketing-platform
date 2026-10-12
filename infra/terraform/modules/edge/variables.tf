variable "name_prefix" {
  description = "Prefix for every resource name."
  type        = string
}

variable "subnet_ids" {
  description = "Subnets of the VPC link."
  type        = list(string)
}

variable "security_group_id" {
  description = "Security group of the VPC link."
  type        = string
}

variable "service_discovery_arn" {
  description = "AWS Cloud Map service of the application."
  type        = string
}

variable "jwt_issuer" {
  description = "Issuer of the tokens (Cognito user pool URL)."
  type        = string
}

variable "jwt_audience" {
  description = "Accepted audiences (Cognito app client IDs)."
  type        = list(string)
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

variable "log_retention_days" {
  description = "Days the access logs are kept."
  type        = number
  default     = 14
}
