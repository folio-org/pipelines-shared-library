variable "aws_region" {
  type        = string
  default     = "us-west-2"
  description = "Rancher AWS region for S3 buckets"
}

variable "s3_access_key" {
  type        = string
  description = "AWS s3 access key"
}

variable "s3_secret_key" {
  type        = string
  description = "AWS s3 secret key"
}
variable "s3_data_export_access_key" {
  type        = string
  description = "AWS s3 access key for data export"
}

variable "s3_data_export_secret_key" {
  type        = string
  description = "AWS s3 secret key for data export"
}

variable "rancher_cluster_name" {
  type        = string
  description = "Rancher cluster name"
}

variable "rancher_project_name" {
  type        = string
  description = "Rancher project name"
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

variable "psql_dump_temporary_storage_size" {
  type        = string
  default     = "1Gi"
  description = "Size of attached ebs volume to psql dump container as temporary storage"
}

variable "s3_postgres_backups_access_key" {
  type        = string
  default     = ""
  description = "AWS s3 postgres backups bucket access key"
}

variable "s3_postgres_backups_secret_key" {
  type        = string
  default     = ""
  description = "AWS s3 postgres backups bucket secret key"
}

variable "s3_backup_path" {
  type        = string
  default     = "s3://folio-postgresql-backups"
  description = "AWS s3 postgres backups bucket name"
}

variable "db_backup_name" {
  type        = string
  description = "Name of DB backup on s3 bucket"
}

variable "docker_folio_dev_registry_username" {
  type        = string
  description = "Username for docker registry"
}

variable "docker_folio_dev_registry_password" {
  type        = string
  description = "Password for docker registry"
}
