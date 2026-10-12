variable "region" {
  description = "AWS region of the project."
  type        = string
  default     = "us-east-1"
}

variable "name_prefix" {
  description = "Prefix for every resource name."
  type        = string
  default     = "ticketing-"
}

variable "github_repository" {
  description = "GitHub repository (owner/name) whose release workflow may push images."
  type        = string
  default     = "whymvm/ticketing-platform"
}

variable "monthly_budget_usd" {
  description = "Monthly cost budget of the account, in USD."
  type        = number
  default     = 20
}

variable "alert_email" {
  description = "Email that receives the budget alerts."
  type        = string
}
