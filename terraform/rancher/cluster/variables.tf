variable "aws_region" {
  type        = string
  default     = "us-west-2"
  description = "Rancher AWS region for S3 buckets"
}

variable "aws_access_key_id" {
  type        = string
  description = "AWS Access Key ID"
}

variable "aws_secret_access_key" {
  type        = string
  description = "AWS Secret Access Key"
}

variable "admin_users" {
  type        = string
  default     = ""
  description = "Comma separated list of admin users"
}

variable "register_in_rancher" {
  type        = bool
  default     = true
  description = "True if eks cluster should be registered in Rancher"
}

variable "rancher_server_url" {
  type        = string
  default     = "https://rancher.ci.folio.org/v3"
  description = "Rancher server URL"
}

variable "rancher_token_key" {
  type        = string
  default     = ""
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
  default     = "ON_DEMAND"
  description = "Type of capacity associated with the EKS Node Group. Valid values: ON_DEMAND, SPOT(for testing purposes only)"
}

variable "eks_nodes_group_size" {
  type = object({
    min_size : number,
    max_size : number,
  })
  default     = { "min_size" : 2, "max_size" : 4 }
  description = "Minimum and maximum number of instances/nodes"
}

variable "asg_instance_types" {
  type        = list(string)
  default     = ["r5a.2xlarge"]
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

variable "index_policy_name" {
  type        = string
  default     = "logstash_policy"
  description = "A policy name cannot start with an underscore and cannot contain a comma or a space."
}

variable "index_template_name" {
  type        = string
  default     = "logstash_template"
  description = "A unique identifier for this template."
}

variable "grafana_admin_password" {
  type        = string
  default     = "SuperSecret"
  description = "GitHub OAuth client ID"
}

variable "github_client_id" {
  type        = string
  default     = ""
  description = "Password for Grafana admin user"
}

variable "github_client_secret" {
  type        = string
  default     = ""
  description = "GitHub OAuth client Secret"
}

variable "kubecost_licence_key" {
  type        = string
  description = "Apply business or enterprise product license key"
}

variable "deploy_kubecost" {
  type        = bool
  default     = true
  description = "Deploy Kubecost tool if true"
}

variable "deploy_sorry_cypress" {
  type        = bool
  default     = false
  description = "Deploy Sorry Cypress tool if true"
}

variable "enable_logging" {
  type        = bool
  default     = false
  description = "Deploy ELK stack if true"
}

variable "enable_monitoring" {
  type        = bool
  default     = true
  description = "Deploy Prometheus monitor if true"
}

variable "slack_webhook_url" {
  type        = string
  description = "Apply slack api url for slack webhook"
}
