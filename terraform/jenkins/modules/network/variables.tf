variable "prefix" {
  type        = string
  description = "Name prefix for tagging and resource naming."
}

variable "vpc_cidr" {
  type        = string
  description = "CIDR block for the new VPC."
}

variable "availability_zones" {
  type        = list(string) # Accept a list of AZs
  description = "List of at least two Availability Zones for the subnets."
}

variable "aws_region" {
  type        = string
  description = "AWS region."
}

variable "tags" {
  type        = map(any)
  default     = {}
  description = "Default tags"
}