variable "prefix" {
  type        = string
  description = "Name prefix for the Bastion host resources."
}

variable "vpc_id" {
  type        = string
  description = "VPC ID where the bastion will be deployed."
}

variable "subnet_id" {
  type        = string
  description = "Public Subnet ID where the bastion will reside."
}

variable "ami_id" {
  type        = string
  description = "AMI ID for the Bastion host."
}

variable "instance_type" {
  type        = string
  description = "EC2 instance type for the Bastion."
}

variable "allowed_cidrs" {
  type        = list(string)
  description = "List of allowed CIDRs for SSH inbound access to the Bastion."
}

variable "key_pair_name" {
  type        = string
  description = "Name of the existing EC2 Key Pair to associate with the Bastion instance for SSH."
}

variable "jenkins_sg_id" {
  type        = string
  description = "Security Group ID of the Jenkins instance. This module will allow inbound SSH from the Bastion to Jenkins."
}

variable "tags" {
  type        = map(any)
  default     = {}
  description = "Default tags"
}