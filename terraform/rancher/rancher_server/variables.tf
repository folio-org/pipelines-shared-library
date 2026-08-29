variable "aws_region" {
  type        = string
  default     = "us-west-2"
  description = "Rancher AWS region for S3 buckets"
}

variable "rancher_version" {
  type    = string
  default = "2.13.3" # target version; actual running state is 2.8.1 — see upgrade notes below
  # SEQUENTIAL UPGRADE REQUIRED — Rancher does not support skipping minor versions.
  #
  # IMPORTANT — K8s 1.34 COMPATIBILITY NOTE:
  #   EKS is on K8s 1.34.x. Rancher stable-channel charts ≤ 2.12.3 declare kubeVersion
  #   constraints that exclude K8s 1.34 (e.g. "< 1.34.0-0"), blocking `terraform apply`.
  #   Intermediate upgrades (2.9–2.12) are performed via rancher-intermediate-upgrade.sh,
  #   which pulls each chart, patches its kubeVersion to ">=1.25.0-0", and upgrades from
  #   the local patched copy (helm upgrade --install supports no kubeVersion bypass flag).
  #
  # Run rancher-intermediate-upgrade.sh first, then `terraform apply` for the final hop:
  #   ./rancher-intermediate-upgrade.sh    # 2.8.1 → 2.9.3 → 2.10.3 → 2.11.3 → 2.12.3
  #   terraform apply                      # 2.12.3 → 2.13.3 (native K8s 1.34 support)
  description = "Rancher Helm chart version. Must be upgraded sequentially through each minor version."
}

variable "rancher_chart_repository" {
  type    = string
  default = "https://releases.rancher.com/server-charts/latest"
  # rancher-stable tops out at 2.12.x (kubeVersion < 1.34.0-0) — incompatible with EKS 1.34.
  # rancher-latest carries 2.13.x which declares kubeVersion < 1.35.0-0 and supports K8s 1.34.
  # Switch back to the stable channel URL once 2.13.x is promoted:
  #   https://releases.rancher.com/server-charts/stable
  description = "Rancher Helm chart repository URL. Use rancher-latest while 2.13.x is not yet in rancher-stable."
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