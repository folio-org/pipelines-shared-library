variable "aws_region" {
  type        = string
  default     = "us-west-2"
  description = "Rancher AWS region for S3 buckets"
}

variable "rancher_version" {
  type        = string
  default     = "2.6.5"
  description = "Rancher version"
}

variable "rancher_cluster_name" {
  type        = string
  default     = "rancher"
  description = "Rancher cluster name"
}

variable "rancher_hostname" {
  type        = string
  default     = "rancher.ci.folio.org"
  description = "Rancher hostname"
}

variable "rancher_server_url" {
  type        = string
  default     = "https://rancher.ci.folio.org/v3"
  description = "Rancher server URL"
}

variable "rancher_token_key" {
  type        = string
  description = "Rancher token key"
}

