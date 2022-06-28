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
  default     = "https://rancher.dev.folio.org/v3"
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

#GitHub variables
variable "repository" {
  type        = string
  description = "GitHub source repository with modules install JSON 'platform-core' or 'platform-complete'"
  default     = "platform-complete"
  validation {
    condition     = can(regex("^(platform-core|platform-complete)$", var.repository))
    error_message = "Wrong repository value! Possible values 'platform-core' or 'platform-complete'."
  }
}

variable "branch" {
  type        = string
  default     = "snapshot"
  description = "GitHub branch of source repository"
}

variable "install_json" {
  type        = string
  default     = ""
  description = "Modules list in JSON string format"
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
variable "okapi_version" {
  type        = string
  description = "Okapi module version"
}

variable "tenant_id" {
  type        = string
  default     = "diku"
  description = "Default tenant id"
}

variable "admin_user" {
  type = map(string)
  default = {
    username = "diku_admin",
    password = "admin"
  }
  description = "Default admin user"
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

variable "frontend_image_tag" {
  type        = string
  description = "Release tag or branch"
}

variable "env_config" {
  type        = string
  default     = "development"
  description = "Configuration file for modules"
  validation {
    condition     = can(regex("^(development|performance|testing)$", var.env_config))
    error_message = "Wrong env_config value! Possible values 'development' 'performance' 'testing'."
  }
}

variable "github_team_ids" {
  type        = list(string)
  default     = []
  description = "List of github team IDs for project access"
}

# PostgreSQL variables
variable "pg_embedded" {
  type        = bool
  default     = true
  description = "Embedded PostgreSQL if true and AWS RDS Aurora if false"
}

variable "pg_version" {
  type        = string
  default     = "12.7"
  description = "Postgres version"
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
variable "kafka_embedded" {
  type        = bool
  default     = true
  description = "Embedded Kafka if true and AWS Kafka if false"
}

variable "kafka_version" {
  type        = string
  default     = "2.8.0"
  description = "Postgres version"
}

variable "kafka_instance_type" {
  type    = string
  default = "kafka.m5.large"
}

variable "kafka_number_of_broker_nodes" {
  type    = number
  default = 2
}

variable "kafka_ebs_volume_size" {
  type    = number
  default = 100 #Increase if not enough
}

# Elasticsearch variables
variable "es_embedded" {
  type        = bool
  default     = true
  description = "Embedded Elasticsearch if true and AWS OpenSearch if false"
}

variable "es_version" {
  type        = string
  default     = "7.9"
  description = "Elasticsearch version"
}

variable "es_create_service_link_role" {
  type    = bool
  default = false
}

variable "es_dedicated_master" {
  type    = bool
  default = false
}

variable "es_instance_count" {
  type    = number
  default = 2
}

variable "es_instance_type" {
  type    = string
  default = "m5.xlarge.search"
}

variable "es_ebs_volume_size" {
  type    = number
  default = 100 #Increase if not enough
}

variable "es_username" {
  default = "esadmin"
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

variable "s3_postgres-backups-bucket-name" {
  type        = string
  default     = "s3://folio-postgresql-backups"
  description = "Path of s3 bucket for backups"
}

variable "s3_postgres_backups_access_key" {
  type        = string
  description = "AWS access key"
}

variable "s3_postgres_backups_secret_key" {
  type        = string
  description = "AWS secret key"
}
