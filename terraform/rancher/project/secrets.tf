resource "rancher2_secret" "db-connect-modules" {
  name         = "db-connect-modules"
  project_id   = rancher2_project.this.id
  namespace_id = rancher2_namespace.this.name
  data = {
    ENV                    = base64encode(local.env_name)
    OKAPI_URL              = base64encode("http://okapi:9130")
    OKAPI_HOST             = base64encode("okapi")
    OKAPI_PORT             = base64encode("9130")
    DB_HOST                = base64encode(var.pg_embedded ? "postgresql" : module.rds.this_rds_cluster_endpoint)
    DB_PORT                = base64encode("5432")
    DB_USERNAME            = base64encode(var.pg_embedded ? var.pg_username : module.rds.this_rds_cluster_master_username)
    DB_PASSWORD            = base64encode(local.pg_password)
    DB_DATABASE            = base64encode(var.pg_dbname)
    DB_MAXPOOLSIZE         = base64encode("5")
    DB_CHARSET             = base64encode("UTF-8")
    DB_QUERYTIMEOUT        = base64encode("60000")
    KAFKA_HOST             = base64encode(var.kafka_embedded ? "kafka" : element(split(":", aws_msk_cluster.this[0].bootstrap_brokers), 0))
    KAFKA_PORT             = base64encode("9092")
    ELASTICSEARCH_URL      = base64encode(var.es_embedded ? "http://elasticsearch-${rancher2_project.this.name}:9200" : "https://${module.aws_es.endpoint}:443")
    ELASTICSEARCH_HOST     = base64encode(var.es_embedded ? "" : module.aws_es.endpoint)
    ELASTICSEARCH_PORT     = base64encode(var.es_embedded ? "9200" : "443")
    ELASTICSEARCH_USERNAME = base64encode(var.es_embedded ? "" : var.es_username)
    ELASTICSEARCH_PASSWORD = base64encode(var.es_embedded ? "" : random_password.es_password[0].result)
  }
}

resource "rancher2_secret" "project-config" {
  name         = "project-config"
  project_id   = rancher2_project.this.id
  namespace_id = rancher2_namespace.this.name
  data = {
    OKAPI_URL    = base64encode(join("", ["https://", local.okapi_url]))
    TENANT_ID    = base64encode(var.tenant_id)
    PROJECT_NAME = base64encode(rancher2_project.this.name)
    PROJECT_ID   = base64encode(element(split(":", rancher2_project.this.id), 1))
  }
}

resource "rancher2_secret" "s3-config-data-worker" {
  name         = "s3-credentials"
  project_id   = rancher2_project.this.id
  namespace_id = rancher2_namespace.this.name
  data = {
    AWS_URL               = base64encode(var.s3_embedded ? join("", ["https://", local.minio_url]) : "https://s3.amazonaws.com")
    AWS_REGION            = base64encode(var.s3_embedded ? "" : var.aws_region)
    AWS_BUCKET            = base64encode(join("-", [data.rancher2_cluster.this.name, rancher2_project.this.name, "data-worker"]))
    AWS_ACCESS_KEY_ID     = base64encode(var.s3_embedded ? random_string.access_key[0].result : var.s3_access_key)
    AWS_SECRET_ACCESS_KEY = base64encode(var.s3_embedded ? random_password.secret_access_key[0].result : var.s3_secret_key)
  }
}

resource "rancher2_secret" "s3-config-data-export" {
  name         = "s3-credentials-data-export"
  project_id   = rancher2_project.this.id
  namespace_id = rancher2_namespace.this.name
  data = {
    AWS_URL               = base64encode(var.s3_embedded ? join("", ["https://", local.minio_url]) : "https://s3.amazonaws.com")
    AWS_REGION            = base64encode(var.s3_embedded ? "" : var.aws_region)
    AWS_BUCKET            = base64encode(join("-", [data.rancher2_cluster.this.name, rancher2_project.this.name, "data-export"]))
    AWS_ACCESS_KEY_ID     = base64encode(var.s3_embedded ? random_string.access_key[0].result : var.s3_access_key)
    AWS_SECRET_ACCESS_KEY = base64encode(var.s3_embedded ? random_password.secret_access_key[0].result : var.s3_secret_key)
  }
}

# For edge* modules
resource "rancher2_secret" "ephemeral-properties" {
  name         = "ephemeral-properties"
  description  = "For edge modules"
  project_id   = rancher2_project.this.id
  namespace_id = rancher2_namespace.this.name
  data = {
    "ephemeral.properties" = base64encode(
      templatefile("${path.module}/resources/ephemeral.properties.tftpl",
        {
          tenant              = var.tenant_id,
          edge_tenants        = join(",", setsubtract(distinct(flatten([for i in local.edge_ephemeral_properties : keys(i)])), [var.tenant_id])),
          admin_user_username = var.admin_user.username,
          admin_user_password = var.admin_user.password,
          edge_properties     = local.edge_ephemeral_properties
        }
      )
    )
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
  depends_on   = [rancher2_namespace.project-namespace]
  name         = "s3-postgres-backups-credentials"
  project_id   = rancher2_project.project.id
  namespace_id = rancher2_namespace.project-namespace.name
  data = {
    RANCHER_CLUSTER_PROJECT_NAME = base64encode(join("/", [data.rancher2_cluster.cluster.name, rancher2_project.project.name]))
    AWS_BUCKET                   = base64encode(var.s3_postgres-backups-bucket-name)
    AWS_ACCESS_KEY_ID            = base64encode(var.s3_postgres_backups_access_key)
    AWS_SECRET_ACCESS_KEY        = base64encode(var.s3_postgres_backups_secret_key)
  }
}
