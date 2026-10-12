variable "name_prefix" {
  description = "Prefix for every resource name."
  type        = string
}

variable "deletion_protection" {
  description = "Prevents deleting the user pool (and its users) by mistake."
  type        = bool
  default     = true
}
