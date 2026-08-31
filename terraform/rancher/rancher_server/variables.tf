variable "aws_region" {
  type        = string
  default     = "us-west-2"
  description = "Rancher AWS region for S3 buckets"
}

variable "rancher_version" {
  type    = string
  default = "2.14.3"
  # SEQUENTIAL UPGRADE REQUIRED — Rancher does not support skipping minor versions.
  # Next minor upgrade: bump to 2.15.x once available and update rancher2 provider to ~>15.0.
  description = "Rancher Helm chart version. Must be upgraded sequentially through each minor version."
}

variable "rancher_chart_repository" {
  type    = string
  default = "https://releases.rancher.com/server-charts/stable"
  # 2.14.3 is available on the stable channel (kubeVersion < 1.36.0-0), which supports EKS 1.34.
  description = "Rancher Helm chart repository URL."
}

variable "rancher_cluster_name" {
  type        = string
  default     = "rancher"
  description = "Rancher cluster name"
}

variable "root_domain" {
  type        = string
  default     = "ci.folio.org"
  description = "Root domain name for Route53"
}

variable "rancher_hostname" {
  type        = string
  default     = "rancher.ci.folio.org"
  description = "Rancher hostname"
}

variable "rancher_token_key" {
  type        = string
  description = "Rancher token key"
}

variable "kubecost_licence_key" {
  type        = string
  description = "Apply business or enterprise product license key"
}

variable "aws_kubecost_access_key_id" {
  type        = string
  description = "ACCESS KEY ID for Kubecost user"
}

variable "aws_kubecost_secret_access_key" {
  type        = string
  description = "SECRET KEY ID for Kubecost user"
}

# Set name of parameter if want to deploy Opensearch Dashboard (ex. folio-opensearch). Left empty "" if not deploy
variable "opensearch_shared_name" {
  type        = string
  default     = "folio-opensearch"
  description = "Name of shared OpenSearch cluster"
}

# Set name of parameter if want to deploy Kafka UI (ex. folio-kafka). Left empty "" if not deploy
variable "kafka_shared_name" {
  type        = string
  default     = "folio-kafka"
  description = "Name of shared MSK cluster"
}