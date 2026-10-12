variable "name_prefix" {
  description = "Prefix for every resource name."
  type        = string
}

variable "cidr_block" {
  description = "CIDR block of the VPC."
  type        = string
  default     = "10.20.0.0/16"
}

variable "app_port" {
  description = "Port where the application listens."
  type        = number
}
