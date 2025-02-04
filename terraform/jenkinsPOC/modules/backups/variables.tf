variable "name" {
  type        = string
  description = "Prefix name for backup resources."
}

variable "dlm_role_arn" {
  type        = string
  description = "IAM Role ARN for DLM if required. If empty, default is used."
  default     = ""
}

variable "tags" {
  type        = map(any)
  default     = {}
  description = "Default tags"
}