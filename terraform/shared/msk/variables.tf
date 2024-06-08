# General variables
variable "aws_region" {
  type        = string
  default     = "us-west-2"
  description = "Rancher AWS region for S3 buckets"
}

variable "service_name" {
  type        = string
  default     = "folio-kafka"
  description = "Name of MSK cluster"
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

variable "vpc_name" {
  type        = string
  default     = "folio-rancher-vpc"
  description = "Name for VPC resource"
}

# Kafka variables
variable "kafka_version" {
  type        = string
  default     = "3.5.1"
  description = "Kafka version"
}

variable "kafka_instance_type" {
  type    = string
  default = "kafka.m5.large"
}

variable "kafka_number_of_broker_nodes" {
  type    = number
  default = 2
}

variable "kafka_ebs_volume_size" {
  type    = number
  default = 100
}
