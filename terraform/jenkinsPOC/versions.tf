terraform {
  required_version = ">= 1.6.1"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "5.84.0"
    }
  }
}

variable "vpc_id" {
  description = "The ID of the VPC where resources are deployed (folio-jenkins-vpc)"
  type        = string
}

variable "private_subnet_id" {
  description = "The ID of the private subnet for the Jenkins instance (folio-jenkins-vpc-private-us-west-2a)"
  type        = string
}

variable "public_subnet_ids" {
  description = "List of public subnet IDs to host the ALB"
  type        = list(string)
}

variable "jenkins_version" {
  description = "The Jenkins version to install"
  type        = string
  default     = "2.479.3"
}

variable "instance_type" {
  description = "EC2 instance type for Jenkins"
  type        = string
  default     = "m7a.xlarge"
}

variable "volume_size" {
  description = "Root EBS volume size in GB"
  type        = number
  default     = 500
}

variable "jenkins_plugins" {
  description = "List of Jenkins plugins (by name) to be installed"
  type        = list(string)
}

variable "dns_name" {
  description = "DNS record name for public access to Jenkins (e.g. jenkins.example.com)"
  type        = string
}

variable "zone_id" {
  description = "The Route53 Hosted Zone ID in which to create the DNS record"
  type        = string
}

variable "backup_bucket" {
  description = "S3 bucket name to store Jenkins backups"
  type        = string
  default     = "my-jenkins-backup-bucket"
}