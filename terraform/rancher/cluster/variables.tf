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

variable "vpc_name" {
  type        = string
  default     = "folio-rancher-vpc"
  description = "VPC name"
}

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
  default     = { "min_size" : 4, "max_size" : 8, "desired_size" : 4 }
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
  }
  description = "Default tags"
}
