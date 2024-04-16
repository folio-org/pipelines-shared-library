# General variables
variable "aws_region" {
  type        = string
  default     = "us-west-2"
  description = "Rancher AWS region for S3 buckets"
}

variable "vpc_name" {
  type        = string
  default     = "folio-rancher-vpc"
  description = "Name for VPC resource"
}

# Elasticsearch variables
variable "os_version" {
  type        = string
  default     = "OpenSearch_2.11"
  description = "Elasticsearch version"
}

variable "service_name" {
  type    = string
  default = "folio-opensearch"
}

variable "os_create_service_link_role" {
  type    = bool
  default = false
}

variable "os_dedicated_master" {
  type    = bool
  default = false
}

variable "os_instance_count" {
  type    = number
  default = 2
}

variable "os_instance_type" {
  type    = string
  default = "r6g.xlarge.elasticsearch"
}

variable "os_ebs_volume_size" {
  type    = number
  default = 200
}

variable "os_username" {
  default = "esadmin"
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
