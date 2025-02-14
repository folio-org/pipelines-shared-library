variable "region" {
  type        = string
  default     = "us-west-2"
  description = "AWS region for resources provision"
}

variable "prefix" {
  type        = string
  description = "A name prefix to use for all resources."
  default     = "folio"
}

variable "vpc_cidr" {
  type        = string
  default     = "192.168.0.0/16"
  description = "CIDR block for the new VPC."
}

variable "allowed_cidrs" {
  type        = list(string)
  default     = ["0.0.0.0/0"]
  description = "List of allowed CIDRs for SSH inbound access to the Bastion."
}

variable "ami_id" {
  type        = string
  description = "Amazon Linux 2023 AMI ID."
  # Example default; replace with the actual AL2023 AMI in your region
  default = "ami-0c2d06d50ce30b442"
}

variable "bastion_instance_type" {
  type        = string
  default     = "t3.micro"
  description = "EC2 instance type for the Bastion."
}

variable "instance_type" {
  type        = string
  description = "EC2 instance type for Jenkins."
  default     = "m7a.xlarge"
}

variable "volume_size" {
  type        = number
  description = "Size of the Jenkins EBS volume in GB."
  default     = 500
}

variable "volume_type" {
  type        = string
  description = "Type of the EBS volume."
  default     = "gp3"
}

variable "enable_restore" {
  type        = bool
  description = "Whether to restore from a given EBS snapshot."
  default     = false
}

variable "restore_snapshot_id" {
  type        = string
  description = "The snapshot ID to restore if enable_restore = true."
  default     = ""
}

variable "jenkins_version" {
  type        = string
  description = "The version of Jenkins to install."
  default     = "2.479.3"
}

variable "backup_bucket" {
  type        = string
  description = "Name of the S3 bucket used for Jenkins backups."
  default     = "my-jenkins-backup-bucket"
}

variable "route53_zone_id" {
  type        = string
  description = "Route53 Hosted Zone ID where DNS record will be created."
}

variable "route53_record_name" {
  type        = string
  description = "DNS record name to point to the ALB."
  default     = "jenkins.example.com"
}

variable "certificate_arn" {
  type        = string
  description = "ARN of the ACM certificate for the ALB."
}

variable "ssh_key_name" {
  type        = string
  description = "SSH key name."
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