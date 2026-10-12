variable "name_prefix" {
  description = "Prefix for every resource name."
  type        = string
}

variable "vpc_id" {
  description = "VPC of the service."
  type        = string
}

variable "subnet_ids" {
  description = "Subnets where the tasks run."
  type        = list(string)
}

variable "security_group_id" {
  description = "Security group of the tasks."
  type        = string
}

variable "container_image" {
  description = "Full image reference, e.g. <account>.dkr.ecr.<region>.amazonaws.com/ticketing-app:1.0.0."
  type        = string
}

variable "app_port" {
  description = "Port where the application listens."
  type        = number
}

variable "cpu" {
  description = "CPU units of the task (256 = 0.25 vCPU)."
  type        = number
  default     = 256
}

variable "memory" {
  description = "Memory of the task in MiB. With 256 CPU units Fargate accepts 512, 1024 or 2048."
  type        = number
  default     = 1024
}

variable "desired_count" {
  description = "Tasks when the service is created. Set 0 to stop it without destroying anything."
  type        = number
  default     = 1
}

variable "max_count" {
  description = "Maximum number of tasks of the autoscaling: a cost ceiling."
  type        = number
  default     = 2
}

variable "environment" {
  description = "Environment variables of the application (table names, queue URL)."
  type        = map(string)
}

variable "admin_key_parameter_arn" {
  description = "ARN of the SecureString SSM parameter with the admin key. It is created outside Terraform, so its value never reaches the state."
  type        = string
}

variable "table_arns" {
  description = "DynamoDB tables the application reads and writes."
  type        = list(string)
}

variable "queue_arns" {
  description = "SQS queues the application sends to and consumes from."
  type        = list(string)
}

variable "log_retention_days" {
  description = "Days the application logs are kept."
  type        = number
  default     = 14
}
