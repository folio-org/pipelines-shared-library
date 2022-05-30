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

#variable "rancher_project_name" {
#  type        = string
#  description = "Rancher project name"
#}

#variable "rancher_project_type" {
#  type        = string
#  description = "Rancher project type (karate, cypress, scratch, perf)"
#}

variable "folio_repository" {
  type        = string
  default     = "core"
  description = "Folio repository for modules. Should be 'core' or 'complete'"
}

variable "folio_release" {
  type        = string
  default     = "snapshot"
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
  type        = string
  default     = true
  description = "Define if embedded or external Postgresql instance needed"
}

variable "folio_embedded_es" {
  type        = string
  default     = true
  description = "Define if embedded or external Elasticsearch instance needed"
}

variable "folio_embedded_kafka" {
  type        = string
  default     = true
  description = "Define if embedded or external Kafka instance needed"
}

variable "kafka_version" {
  type        = string
  default     = "2.8.0"
  description = "Postgres version"
}

variable "pg_version" {
  type        = string
  default     = "12"
  description = "Postgres version"
}

variable "pg_subversion" {
  type        = string
  default     = "7"
  description = "Postgres subversion"
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
  default     = "4.11.1"
  description = "Release tag or branch"
}

variable "stripes_image_tag" {
  type        = string
  description = "Release tag or branch"
}


variable "env_type" {
  type        = string
  default     = "development"
  description = "config file for dev, perf, test env"

  validation {
    condition = contains(["development", "performance"], var.env_type)
    #condition     = var.env_type == "performance" || var.env_type == "testing"
    error_message = "Must be a valid env type for perf or test env."
  }
}


//TODO Check if needed
#variable "github_team_ids" {
#  type = map(any)
#  default = {
#    "folijet"         = "github_team://2826211"
#    "vega"            = "github_team://2956598"
#    "concorde"        = "github_team://3198396"
#    "ebsco-core"      = "github_team://2733306"
#    "firebird"        = "github_team://3703817"
#    "gulfstream"      = "github_team://3711746"
#    "leipzig"         = "github_team://3177353"
#    "thunderjet"      = "github_team://2937171"
#    "spitfire"        = "github_team://2946231"
#    "stripes"         = "github_team://2108101"
#    "unam"            = "github_team://2676560"
#    "erm"             = "github_team://2869610"
#    "ncip"            = "github_team://3704056"
#    "core-functional" = "github_team://3936584"
#    "stripes-force"   = "github_team://4073724"
#    "ptf"             = "github_team://4195625"
#    "falcon"          = "github_team://4318841"
#    "thor"            = "github_team://3970386"
#    "volaris"         = "github_team://4686360"
#  }
#}
