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
  default     = "folio-rancher"
  description = "Rancher cluster name"
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

variable "root_domain" {
  type        = string
  default     = "ci.folio.org"
  description = "Root domain name for Route53"
}

variable "aws_kubecost_access_key_id" {
  type        = string
  description = "ACCESS KEY ID for Kubecost user"
}

variable "aws_kubecost_secret_access_key" {
  type        = string
  description = "SECRET KEY ID for Kubecost user"
}

variable "projectID" {
  type        = string
  default     = "732722833398"
  description = "The AWS AccountID where the Athena CUR is. Generally your masterpayer account"
}

# Set name of parameter if want to deploy Opensearch Dashboard (ex. folio-opensearch)
variable "opensearch_shared_name" {
  type        = string
  default     = ""
  description = "Name of shared OpenSearch cluster"
}

# Set name of parameter if want to deploy Kafka UI (ex. folio-kafka)
variable "kafka_shared_name" {
  type        = string
  default     = ""
  description = "Name of shared MSK cluster"
}
