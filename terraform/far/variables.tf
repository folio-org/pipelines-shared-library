variable "aws_region" {
  description = "AWS region where resources will be created"
  type        = string
  default     = "us-west-2"
}

variable "rancher_server_url" {
  description = "Rancher server URL"
  type        = string
  default     = "https://rancher.ci.folio.org/v3"
}

variable "rancher_token_key" {
  description = "Rancher API token key for authentication"
  type        = string
  sensitive   = true
}

variable "cluster_name" {
  description = "Name of the Rancher cluster where resources will be deployed"
  default     = "rancher"
  type        = string
}

variable "project_name" {
  description = "Name of the Rancher project/namespace"
  default     = "folio-applications-registry"
  type        = string
}

variable "domain_name" {
  description = "Domain name for the applications"
  type        = string
  default     = "far.ci.folio.org"
}

variable "volume_size" {
  description = "Size of the EBS volume in GiB"
  type        = number
  default     = 100
}

variable "volume_type" {
  description = "Type of the EBS volume"
  type        = string
  default     = "gp3"
}

variable "tags" {
  description = "Tags to apply to all resources"
  type        = map(string)
  default = {
    Terraform   = "true"
    Environment = "production"
    Project     = "folio"
    Team        = "kitfox"
  }
}

variable "postgres_chart_version" {
  description = "Version of the PostgreSQL Helm chart"
  type        = string
  default     = "16.7.21"
}

variable "mgr_chart_version" {
  description = "Version of the mgr-applications Helm chart"
  type        = string
  default     = "0.0.15"
}

variable "snapshot_id" {
  description = "The ID of the EBS snapshot to restore from. If not provided, a new volume will be created."
  type        = string
  default     = null
}