variable "aws_region" {
  type        = string
  default     = "us-west-2"
  description = "AWS region for resources provision"
}
variable "vpc_name" {
  type        = string
  default     = "folio-rancher-vpc"
  description = "Name for VPC resource"
}

variable "vpc_cidr_block" {
  type        = string
  default     = "10.0.0.0/16"
  description = "CIDR block for VPC"
}

variable "subnet_prefix_extension" {
  type        = number
  default     = 4
  description = "CIDR block bits extension to calculate CIDR blocks of each subnetwork."
}

variable "clusters" {
  type        = list(string)
  default     = ["folio-testing", "folio-dev", "folio-perf", "folio-tmp"]
  description = "List of EKS clusters names expected to be provisioned in VPC"
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
