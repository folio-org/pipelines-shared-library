variable "cluster_name" {
  description = "Name of the Rancher/EKS cluster where the resources will be provisioned."
  type        = string
}

variable "cluster_id" {
  description = "ID of the Rancher cluster"
  type        = string
}

variable "namespace_name" {
  description = "Kubernetes namespace in which to deploy PostgreSQL and its PVC."
  type        = string
}

variable "namespace_id" {
  description = "ID of the Rancher namespace"
  type        = string
}

variable "release_name" {
  description = "Helm release name for the PostgreSQL deployment."
  type        = string
  default     = "postgres"
}

variable "helm_repository" {
  description = "URL of the Helm chart repository."
  type        = string
  default     = "oci://registry-1.docker.io/bitnamicharts"
}

variable "helm_chart" {
  description = "Name of the Helm chart to deploy."
  type        = string
  default     = "postgresql"
}

variable "chart_version" {
  description = "Exact version of the Bitnami PostgreSQL chart to deploy."
  type        = string
  default     = "16.7.14"
}

variable "helm_timeout" {
  description = "Maximum time in seconds to wait for the Helm release to deploy."
  type        = number
  default     = 600
}

variable "db_username" {
  description = "Application database username used by Helm to create a non‑superuser role."
  type        = string
  default     = "far_admin"
}

variable "db_name" {
  description = "Name of the application database to be created."
  type        = string
  default     = "far_db"
}

variable "db_port" {
  description = "Port on which PostgreSQL will listen."
  type        = number
  default     = 5432
}

variable "db_charset" {
  description = "PostgreSQL database charset"
  type        = string
  default     = "UTF-8"
}

variable "db_max_connections" {
  description = "PostgreSQL maximum number of connections"
  type        = string
  default     = "50"
}

variable "db_query_timeout" {
  description = "PostgreSQL query timeout in milliseconds"
  type        = string
  default     = "60000"
}

variable "ebs_size" {
  description = "Size of the EBS volume in GiB."
  type        = number
  default     = 100
}

variable "ebs_type" {
  description = "Type of EBS volume (e.g. gp3, io2)."
  type        = string
  default     = "gp3"
}

variable "ebs_iops" {
  description = "Provisioned IOPS for the EBS volume when using io1/io2/gp3 types."
  type        = number
  default     = 3000
}

variable "ebs_throughput" {
  description = "Throughput in MB/s for gp3 volumes."
  type        = number
  default     = 125
}

variable "filesystem_type" {
  description = "Filesystem type to format the EBS volume (e.g. ext4, xfs)."
  type        = string
  default     = "ext4"
}

variable "password_length" {
  description = "Length of the generated PostgreSQL password."
  type        = number
  default     = 32
}

variable "password_override_special" {
  description = "Custom set of special characters to use when generating passwords."
  type        = string
  default     = "!@#$%^&*()-_=+[]{}<>?"
}

variable "tags" {
  description = "Tags to apply to all AWS resources."
  type        = map(string)
  default     = {}
}

variable "snapshot_id" {
  description = "The ID of the EBS snapshot to restore from. If not provided, a new volume will be created."
  type        = string
  default     = null
}
