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

variable "root_domain" {
  type    = string
  default = "ci.folio.org"
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

variable "rancher_cluster_name" {
  type        = string
  description = "Rancher project name"
}

variable "folio_repository" {
  type        = string
  description = "Folio repository for modules. Should be 'core' or 'complete'"
}

variable "folio_release" {
  type        = string
  description = "Release tag or branch"
}

variable "folio_docker_registry_username" {
  type        = string
  description = "Username for docker registry"
}

variable "folio_docker_registry_password" {
  type        = string
  description = "Password for docker registry"
}

variable "folio_embedded_db" {
  type        = bool
  default     = true
  description = "Define if embedded or external Postgresql instance needed"
}

variable "folio_embedded_es" {
  type        = bool
  default     = true
  description = "Define if embedded or external Elasticsearch instance needed"
}

variable "folio_embedded_kafka" {
  type        = bool
  default     = true
  description = "Define if embedded or external Kafka instance needed"
}

variable "folio_embedded_s3" {
  type        = bool
  default     = true
  description = "Define if embedded or external S3 instance needed"
}

variable "kafka_version" {
  type        = string
  default     = "2.8.0"
  description = "Postgres version"
}

variable "pg_version" {
  type        = string
  default     = "12.7"
  description = "Postgres version"
}

variable "pg_username" {
  type        = string
  default     = "postgres"
  description = "Postgres username"
}

variable "pg_password" {
  type        = string
  description = "Postgres password"
}

variable "pg_dbname" {
  type        = string
  default     = "folio_modules"
  description = "Postgres DB name"
}

variable "pgadmin_username" {
  type        = string
  default     = "user@folio.org"
  description = "Postgres DB name"
}

variable "pgadmin_password" {
  type        = string
  description = "Postgres DB name"
}

variable "tenant_id" {
  type        = string
  default     = "diku"
  description = "Release tag or branch"
}

variable "admin_user" {
  type = map(string)
  default = {
    username = "diku_admin",
    password = "admin"
  }
}

variable "okapi_version" {
  type        = string
  description = "Release tag or branch"
}

variable "stripes_image_tag" {
  type        = string
  description = "Release tag or branch"
}

variable "github_team_ids" {
  type        = list(string)
  default     = []
  description = "List of github team IDs for project access"
}

variable "env_type" {
  type        = string
  default     = "development"
  description = "config file for dev, perf, test env"
}

