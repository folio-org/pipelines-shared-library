resource "rancher2_secret" "db-connect-modules" {
  name         = "db-connect-modules"
  project_id   = rancher2_project.this.id
  namespace_id = rancher2_namespace.this.id
  data = {
    ENV                    = base64encode(local.env_name)
    OKAPI_URL              = base64encode("http://okapi:9130")
    OKAPI_HOST             = base64encode("okapi")
    OKAPI_PORT             = base64encode("9130")
    DB_HOST                = base64encode(var.pg_embedded ? local.pg_service_writer : module.rds[0].cluster_endpoint)
    DB_HOST_READER         = base64encode(var.pg_embedded ? local.pg_service_reader : module.rds[0].cluster_reader_endpoint)
    DB_PORT                = base64encode("5432")
    DB_USERNAME            = base64encode(var.pg_embedded ? var.pg_username : module.rds[0].cluster_master_username)
    DB_PASSWORD            = base64encode(local.pg_password)
    DB_DATABASE            = base64encode(var.pg_dbname)
    DB_MAXPOOLSIZE         = base64encode("5")
    DB_CHARSET             = base64encode("UTF-8")
    DB_QUERYTIMEOUT        = base64encode("60000")
    KAFKA_HOST             = base64encode(var.kafka_shared ? local.msk_value["KAFKA_HOST"] : "kafka-${var.rancher_project_name}")
    KAFKA_PORT             = base64encode("9092")
    ELASTICSEARCH_URL      = base64encode(var.opensearch_shared ? base64decode(local.opensearch_value["ELASTICSEARCH_URL"]) : "http://opensearch-${var.rancher_project_name}:9200")
    ELASTICSEARCH_HOST     = base64encode(var.opensearch_shared ? base64decode(local.opensearch_value["ELASTICSEARCH_HOST"]) : "")
    ELASTICSEARCH_PORT     = base64encode(var.opensearch_shared ? base64decode(local.opensearch_value["ELASTICSEARCH_PORT"]) : "9200")
    ELASTICSEARCH_USERNAME = base64encode(var.opensearch_shared ? base64decode(local.opensearch_value["ELASTICSEARCH_USERNAME"]) : "admin")
    ELASTICSEARCH_PASSWORD = base64encode(var.opensearch_shared ? base64decode(local.opensearch_value["ELASTICSEARCH_PASSWORD"]) : "admin")
    SYSTEM_USER_PASSWORD   = base64encode(random_password.system_user_password.result)
  }
}

resource "rancher2_secret" "project-config" {
  name         = "project-config"
  project_id   = rancher2_project.this.id
  namespace_id = rancher2_namespace.this.name
  data = {
    OKAPI_URL    = base64encode(join("", ["https://", local.okapi_url]))
    TENANT_ID    = base64encode(var.tenant_id)
    PROJECT_NAME = base64encode(var.rancher_project_name)
    PROJECT_ID   = base64encode(element(split(":", rancher2_namespace.this.id), 1))
  }
}

resource "rancher2_secret" "s3-config-data-worker" {
  name         = "s3-credentials"
  project_id   = rancher2_project.this.id
  namespace_id = rancher2_namespace.this.name
  data = {
    AWS_URL               = base64encode(var.s3_embedded ? join("", ["https://", local.minio_url]) : "https://s3.amazonaws.com")
    AWS_REGION            = base64encode(var.aws_region)
    AWS_BUCKET            = base64encode(join("-", [data.rancher2_cluster.this.name, var.rancher_project_name, "data-worker"]))
    AWS_ACCESS_KEY_ID     = base64encode(var.s3_embedded ? random_string.access_key[0].result : var.s3_access_key)
    AWS_SECRET_ACCESS_KEY = base64encode(var.s3_embedded ? random_password.secret_access_key[0].result : var.s3_secret_key)
    LOCAL_FS_BUCKET       = base64encode(var.s3_embedded ? "local-files" : join("-", ["second", data.rancher2_cluster.this.name, var.rancher_project_name, "data-worker"]))
  }
}

resource "rancher2_secret" "s3-config-data-export" {
  name         = "s3-credentials-data-export"
  project_id   = rancher2_project.this.id
  namespace_id = rancher2_namespace.this.name
  data = {
    AWS_URL               = base64encode(var.s3_embedded ? join("", ["https://", local.minio_url]) : "https://s3.amazonaws.com")
    AWS_REGION            = base64encode(var.aws_region)
    AWS_BUCKET            = base64encode(join("-", [data.rancher2_cluster.this.name, var.rancher_project_name, "data-export"]))
    AWS_ACCESS_KEY_ID     = base64encode(var.s3_embedded ? random_string.access_key[0].result : var.s3_access_key)
    AWS_SECRET_ACCESS_KEY = base64encode(var.s3_embedded ? random_password.secret_access_key[0].result : var.s3_secret_key)
  }
}

resource "rancher2_secret" "s3-config-data-import" {
  name         = "s3-credentials-data-import"
  project_id   = rancher2_project.this.id
  namespace_id = rancher2_namespace.this.name
  data = {
    AWS_URL               = base64encode(var.s3_embedded ? join("", ["https://", local.minio_url]) : "https://s3.amazonaws.com")
    AWS_REGION            = base64encode(var.aws_region)
    AWS_BUCKET            = base64encode(join("-", [data.rancher2_cluster.this.name, var.rancher_project_name, "data-import"]))
    AWS_ACCESS_KEY_ID     = base64encode(var.s3_embedded ? random_string.access_key[0].result : var.s3_access_key)
    AWS_SECRET_ACCESS_KEY = base64encode(var.s3_embedded ? random_password.secret_access_key[0].result : var.s3_secret_key)
  }
}
# RANCHER-866
resource "rancher2_secret" "s3-config-oai-pmh" {
  name         = "s3-credentials-oai-pmh"
  project_id   = rancher2_project.this.id
  namespace_id = rancher2_namespace.this.name
  data = {
    AWS_URL               = base64encode(var.s3_embedded ? join("", ["https://", local.minio_url]) : "https://s3.amazonaws.com")
    AWS_REGION            = base64encode(var.aws_region)
    AWS_BUCKET            = base64encode(join("-", [data.rancher2_cluster.this.name, var.rancher_project_name, "oai-pmh"]))
    AWS_ACCESS_KEY_ID     = base64encode(var.s3_embedded ? random_string.access_key[0].result : var.s3_access_key)
    AWS_SECRET_ACCESS_KEY = base64encode(var.s3_embedded ? random_password.secret_access_key[0].result : var.s3_secret_key)
  }
}

# For edge* modules
resource "rancher2_secret" "edge-api-config" {
  name         = "apiconfiguration"
  description  = "For edge-orders module"
  project_id   = rancher2_project.this.id
  namespace_id = rancher2_namespace.this.name
  data = {
    "api_config" = filebase64("${path.module}/resources/api_configuration.json")
  }
}

resource "rancher2_secret" "s3-postgres-backups-credentials" {
  name         = "s3-postgres-backups-credentials"
  project_id   = rancher2_project.this.id
  namespace_id = rancher2_namespace.this.name
  data = {
    AWS_ACCESS_KEY_ID     = base64encode(var.s3_postgres_backups_access_key)
    AWS_SECRET_ACCESS_KEY = base64encode(var.s3_postgres_backups_secret_key)
  }
}

resource "random_password" "system_user_password" {
  length      = 16
  special     = true
  numeric     = true
  upper       = true
  lower       = true
  min_lower   = 1
  min_numeric = 1
  min_special = 1
  min_upper   = 1
}
