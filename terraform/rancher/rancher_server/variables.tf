variable "aws_region" {
  type        = string
  default     = "us-west-2"
  description = "Rancher AWS region for S3 buckets"
}

variable "rancher_version" {
  type        = string
  default     = "2.6.5"
  description = "Rancher version"
}

