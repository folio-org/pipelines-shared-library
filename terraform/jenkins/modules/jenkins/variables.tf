variable "prefix" {
  type        = string
  description = "Name prefix."
}
variable "vpc_id" {
  type        = string
  description = "VPC ID"
}

variable "subnet_id" {
  type        = string
  description = "Subnet ID."
}

variable "ssh_key_name" {
  type        = string
  description = "SSH key name."
}

variable "ami_id" {
  type        = string
  description = "AMI for Jenkins EC2."
}

variable "instance_type" {
  type        = string
  description = "EC2 Instance Type."
}

variable "volume_size" {
  type        = number
  description = "Size of EBS volume in GB."
}

variable "volume_type" {
  type        = string
  description = "Type of EBS volume."
  default     = "gp3"
}

variable "availability_zone" {
  type        = string
  description = "AZ to create the EBS volume in."
}

variable "enable_restore" {
  type        = bool
  description = "Flag to restore from existing snapshot."
  default     = false
}

variable "restore_snapshot_id" {
  type        = string
  description = "Snapshot ID for restore."
  default     = ""
}

variable "jenkins_version" {
  type        = string
  description = "Jenkins version to install."
}

variable "backup_bucket" {
  type        = string
  description = "S3 bucket name for Jenkins backups."
}


variable "tags" {
  type        = map(any)
  default     = {}
  description = "Default tags"
}