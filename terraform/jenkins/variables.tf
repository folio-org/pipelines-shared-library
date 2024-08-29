variable "aws_region" {
  type        = string
  default     = "us-west-2"
  description = "AWS region for resources provision"
}
variable "vpc_name" {
  type        = string
  default     = "folio-jenkins-vpc"
  description = "Name for VPC resource"
}

variable "vpc_cidr_block" {
  type        = string
  default     = "192.168.0.0/16"
  description = "CIDR block for VPC"
}

variable "jenkins_home_size" {
  type        = number
  default     = 500
  description = "Size of the volume to mount /home/jenkins directory"
}

variable "tags" {
  type = map(any)
  default = {
    Terraform          = "true"
    Project            = "folio"
    Team               = "kitfox"
    kubernetes_service = "Folio-Jenkins"
  }
  description = "Default tags"
}

variable "vpc_azs" {
  type        = list(string)
  default     = ["us-west-2a", "us-west-2b"]
  description = "Availability zones to place VPC in"
}
variable "vpc_public_subnets" {
  type        = list(string)
  default     = ["192.168.1.0/24", "192.168.2.0/24"]
  description = "CIDR blocks for public subnets"
}

variable "vpc_private_subnets" {
  type        = list(string)
  default     = ["192.168.101.0/24", "192.168.102.0/24"]
  description = "CIDR blocks for private subnets"
}

variable "ami" {
  type        = string
  default     = "ami-0a38c1c38a15fed74"
  description = "AMI to run Jenkins on"
}

variable "ssh_key_name" {
  type        = string
  default     = "FolioJenkins"
  description = "SSH key to login on Jenkins master"
}

variable "instance_type" {
  type        = string
  default     = "m5.xlarge"
  description = "Instance type for Jenkins master"
}

variable "certificate_arn" {
  type        = string
  default     = "arn:aws:acm:us-west-2:732722833398:certificate/b1e1ca4b-0f0a-41c8-baaa-8b64a1cd4e0a"
  description = "Certificate arn to use on the ALB"
}

variable "route53_zone_id" {
  type        = string
  default     = "Z3T7T50VQ846GQ"
  description = "Route53 zone id to create record in"
}

variable "jenkins_version" {
  type        = string
  default     = "2.462.1"
  description = "Version of Jenkins server to install"
}

variable "agent_ami" {
  type        = string
  default     = "ami-0688ba7eeeeefe3cd"
  description = "AMI to run Jenkins agent on"
}

variable "agent_instance_type" {
  type        = string
  default     = "m5.2xlarge"
  description = "Instance type for Jenkins agents"
}

variable "agents_count" {
  type        = number
  default     = 1
  description = "Count of the Jenkins agents to run"
}

variable "route53_internal_zone_id" {
  type        = string
  default     = "Z02587693OFIQ4WPDRZ5S"
  description = "Route53 zone id to create record in for agents"
}

variable "iam_jenkins_role" {
  type        = string
  default     = "JenkinsRole"
  description = "IAM role name to be assigned to the Jenkins instance"
}

variable "dlm_times" {
  type        = list(string)
  default     = ["23:45"]
  description = "List of times when snapshot must be created"
}

variable "dlm_interval" {
  type        = number
  default     = 24
  description = "Interval between snapshot creating"
}

variable "dlm_retain_count" {
  type        = number
  default     = 14
  description = "Count of the Jenkins snapshot to retain"
}

variable "dlm_tags" {
  type = map(any)
  default = {
    Snapshot = "true"
  }
  description = "Tags for volume to create snapshots"
}
