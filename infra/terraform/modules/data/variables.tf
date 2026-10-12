variable "name_prefix" {
  description = "Prefix for every table name, e.g. \"local-ticketing-\"."
  type        = string
}

variable "point_in_time_recovery" {
  description = "Enables continuous backups (PITR). Recommended in AWS, unnecessary in local."
  type        = bool
  default     = true
}

variable "deletion_protection" {
  description = "Prevents accidental table deletion. Recommended in AWS, disabled in local."
  type        = bool
  default     = true
}

variable "max_request_units" {
  description = "Maximum read and write request units per second of each on-demand table. Null means no maximum (local)."
  type = object({
    read  = number
    write = number
  })
  default = null
}
