variable "aws_region" {
  type        = string
  default     = "us-west-2"
  description = "Rancher AWS region for S3 buckets"
}

variable "rancher_server_url" {
  type        = string
  default     = "https://rancher.dev.folio.org/v3"
  description = "Rancher server URL"
}

variable "rancher_token_key" {
  type        = string
  description = "Rancher token key"
}

variable "root_domain" {
  type    = string
  default = "ci.folio.org"
}

variable "vpc_create" {
  type        = bool
  default     = true
  description = "True if VPC should be created, false if use existing VPC. !!!Be careful subnets of existing VPC should have specific tags!!!"
}

variable "vpc_id" {
  type        = string
  default     = null
  description = "ID of existing vpc"
}

variable "vpc_cidr_block" {
  type        = string
  default     = "10.21.0.0/16"
  description = "Rancher cluster name (testing, scratch, performance)"
}

variable "subnet_prefix_extension" {
  type        = number
  default     = 4
  description = "CIDR block bits extension to calculate CIDR blocks of each subnetwork."
}

variable "zone_offset" {
  type        = number
  default     = 8
  description = "CIDR block bits extension offset to calculate Public subnets, avoiding collisions with Private subnets."
}

#variable "developer_users" {
#  type        = list(string)
#  description = "List of Kubernetes developers."
#}
#
#variable "admin_users" {
#  type        = list(string)
#  description = "List of Kubernetes admins."
#}

variable "eks_nodes_type" {
  type        = string
  default     = "SPOT"
  description = "Type of capacity associated with the EKS Node Group. Valid values: ON_DEMAND, SPOT"
}

variable "eks_node_group_size" {
  type = object({
    min_size : number,
    max_size : number,
    desired_size : number
  })
  default     = { "min_size" : 3, "max_size" : 6, "desired_size" : 3 }
  description = "Minimum, maximum, and desired number of instances/nodes"
}

variable "asg_instance_types" {
  type        = list(string)
  default     = ["m5.xlarge"]
  description = "List of EC2 instance machine types to be used in EKS."
}

variable "tags" {
  type = map(any)
  default = {
    Terraform = "true"
    Project   = "folio"
    Team      = "kitfox"
    Tool      = "rancher"
  }
  description = "Default tags"
}
