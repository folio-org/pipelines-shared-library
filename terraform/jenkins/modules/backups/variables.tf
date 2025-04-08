variable "prefix" {
  type        = string
  description = "Prefix name for backup resources."
}

variable "tags" {
  type        = map(any)
  default     = {}
  description = "Default tags"
}