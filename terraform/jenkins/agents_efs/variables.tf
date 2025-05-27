variable "aws_region" {
  description = "AWS region to deploy resources"
  type        = string
  default     = "us-west-2"
}

variable "eks_cluster_name" {
  description = "Name of the EKS cluster"
  default     = "folio-jenkins-agents"
  type        = string
}

variable "vpc_name" {
  description = "VPC name to deploy EFS in"
  default     = "folio-rancher-vpc"
  type        = string
}

variable "allowed_cidr_blocks" {
  description = "CIDR blocks allowed to access EFS"
  type        = list(string)
  default     = ["10.0.0.0/16", "192.168.0.0/16"]
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