variable "prefix" {
  type        = string
  description = "Name prefix for ALB resources."
}

variable "vpc_id" {
  type        = string
  description = "VPC ID to deploy the ALB into."
}

variable "subnet_ids" {
  type        = list(string)
  description = "Subnet IDs for the ALB."
}

variable "jenkins_instance_id" {
  type        = string
  description = "EC2 instance ID for Jenkins."
}

variable "jenkins_sg_id" {
  type        = string
  description = "Security Group ID of the Jenkins instance."
}

variable "certificate_arn" {
  type        = string
  description = "ARN of an ACM certificate to use for HTTPS."
}

variable "tags" {
  type        = map(any)
  default     = {}
  description = "Default tags"
}