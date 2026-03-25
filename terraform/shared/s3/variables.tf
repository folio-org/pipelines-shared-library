variable "aws_region" {
  type        = string
  default     = "us-west-2"
  description = "Rancher AWS region for S3 buckets"
}

variable "bucket_name" {
  type        = string
  default     = "folio-metrics"
  description = "Name of the S3 bucket for metrics tracking"
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

variable "metrics_retention_days" {
  type        = number
  default     = 90
  description = "Number of days to retain metrics objects before expiration"
}
