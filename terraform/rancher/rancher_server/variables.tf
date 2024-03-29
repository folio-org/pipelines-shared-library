variable "aws_region" {
  type        = string
  default     = "us-west-2"
  description = "Rancher AWS region for S3 buckets"
}

variable "rancher_version" {
  type        = string
  default     = "2.7.1"
  description = "Rancher version"
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