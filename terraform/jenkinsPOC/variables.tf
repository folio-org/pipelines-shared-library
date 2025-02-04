variable "aws_region" {
  type        = string
  default     = "us-west-2"
  description = "AWS region for resources provision"
}

variable "tags" {
  type = map(any)
  default = {
    Terraform = "true"
    Project   = "folio"
    Team      = "kitfox"
  }
  description = "Default tags"
}