# General variables
variable "aws_region" {
  type        = string
  default     = "us-west-2"
  description = "Rancher AWS region for S3 buckets"
}

variable "root_domain" {
  type        = string
  default     = "ci.folio.org"
  description = "Root domain name for Route53"
}

# Rancher variables
variable "rancher_server_url" {
  type        = string
  default     = "https://rancher.ci.folio.org/v3"
  description = "Rancher server URL"
}

variable "rancher_token_key" {
  type        = string
  description = "Rancher token key"
}

variable "rancher_cluster_name" {
  type        = string
  description = "Rancher cluster name"
}

variable "rancher_project_name" {
  type        = string
  description = "Rancher project name"
}

variable "folio_docker_registry_username" {
  type        = string
  description = "Username for docker registry"
}

variable "folio_docker_registry_password" {
  type        = string
  description = "Password for docker registry"
}

#Okapi variables
variable "tenant_id" {
  type        = string
  default     = "diku"
  description = "Default tenant id"
}

variable "github_team_ids" {
  type        = list(string)
  default     = []
  description = "List of github team IDs for project access"
}

variable "github_org_id" {
  type        = string
  default     = "16495055"
  description = "Global GitHub folio-org team id"
}

# PostgreSQL variables
variable "enable_rw_split" {
  type        = bool
  default     = false
  description = "Enable Read/Write split"
}

variable "pgadmin4" {
  type        = bool
  default     = true
  description = "Deploy pgadmin4 tool if true"
}

variable "pg_embedded" {
  type        = bool
  default     = true
  description = "Embedded PostgreSQL if true and AWS RDS Aurora if false"
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

variable "pg_max_conn" {
  type    = number
  default = 5000
}

variable "pg_version" {
  type        = string
  default     = "16.1"
  description = "Postgres version"
}

variable "pg_vol_size" {
  type        = number
  default     = 20
  description = "Postgres EBS volume size"
}

variable "pg_dbname" {
  type        = string
  default     = "folio_modules"
  description = "Postgres DB name"
}

variable "pg_username" {
  type        = string
  default     = "postgres"
  description = "Postgres username"
}

variable "pg_password" {
  type        = string
  default     = ""
  description = "Postgres password"
}

variable "pg_instance_type" {
  type    = string
  default = "db.r5.xlarge"
}

variable "pg_rds_snapshot_name" {
  type    = string
  default = ""
}

# Kafka variables
variable "kafka_shared" {
  type        = bool
  default     = true
  description = "Shared AWS MSK if true and embedded kafka if false"
}

variable "kafka_shared_name" {
  type        = string
  default     = "folio-kafka"
  description = "Name of shared MSK cluster"
}

variable "kafka_number_of_broker_nodes" {
  type    = number
  default = 1
}

variable "kafka_ebs_volume_size" {
  type    = number
  default = 10
}

variable "kafka_max_mem_size" {
  type    = number
  default = 8192
}

# Elasticsearch variables
variable "opensearch_shared" {
  type        = bool
  default     = true
  description = "Shared AWS OpenSearch if true and embedded OpenSearch if false"
}

variable "opensearch_shared_name" {
  type        = string
  default     = "folio-opensearch"
  description = "Name of shared OpenSearch cluster"
}

variable "opensearch_single_node" {
  description = "Deploy OpenSearch in single-node mode (master, data, and client combined)"
  type        = bool
  default     = true
}

variable "es_ebs_volume_size" {
  type    = number
  default = 100
}

# Minio variables
variable "s3_embedded" {
  type        = bool
  default     = true
  description = "Embedded Minio if true and AWS S3 if false"
}

variable "s3_access_key" {
  type        = string
  default     = ""
  description = "AWS s3 access key"
}

variable "s3_secret_key" {
  type        = string
  default     = ""
  description = "AWS s3 secret key"
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

variable "s3_postgres_backups_access_key" {
  type        = string
  description = "AWS access key"
}

variable "s3_postgres_backups_secret_key" {
  type        = string
  description = "AWS secret key"
}

variable "pg_ldp_user_password" {
  type        = string
  description = "Postgresql password for ldp user"
}
