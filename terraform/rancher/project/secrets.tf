#TODO Adjust data
resource "rancher2_secret" "db-connect-modules" {
  depends_on   = [rancher2_namespace.project-namespace]
  name         = "db-connect-modules"
  project_id   = rancher2_project.project.id
  namespace_id = rancher2_namespace.project-namespace.name
  data = {
    DB_HOST                = base64encode("pg-folio")
    DB_PORT                = base64encode("5432")
    DB_USERNAME            = base64encode(var.pg_username)
    DB_PASSWORD            = base64encode(var.pg_password)
    DB_DATABASE            = base64encode(var.pg_dbname)
    DB_MAXPOOLSIZE         = base64encode("5")
    DB_CHARSET             = base64encode("UTF-8")
    DB_QUERYTIMEOUT        = base64encode("60000")
    KAFKA_HOST             = base64encode("kafka")
    KAFKA_PORT             = base64encode("9092")
    ELASTICSEARCH_HOST     = base64encode("")
    ELASTICSEARCH_PORT     = base64encode("9200")
    OKAPI_URL              = base64encode("http://okapi:9130")
    OKAPI_HOST             = base64encode("okapi")
    OKAPI_PORT             = base64encode("9130")
    ENV                    = base64encode(rancher2_project.project.name)
    ELASTICSEARCH_USERNAME = base64encode("")
    ELASTICSEARCH_PASSWORD = base64encode("")
    ELASTICSEARCH_URL      = base64encode("http://elasticsearch-${rancher2_project.project.name}:9200")
  }
}

resource "rancher2_secret" "project-config" {
  depends_on   = [rancher2_namespace.project-namespace]
  name         = "project-config"
  project_id   = rancher2_project.project.id
  namespace_id = rancher2_namespace.project-namespace.name
  data = {
    OKAPI_URL    = base64encode(join("", ["https://", join(".", [join("-", [data.rancher2_cluster.cluster.name, rancher2_project.project.name, "okapi"]), var.root_domain])]))
    TENANT_ID    = base64encode(var.tenant_id)
    PROJECT_NAME = base64encode(rancher2_project.project.name)
    PROJECT_ID   = base64encode(element(split(":", rancher2_project.project.id), 1))
  }
}

resource "rancher2_secret" "s3-credentials" {
  depends_on   = [rancher2_namespace.project-namespace]
  name         = "s3-credentials"
  project_id   = rancher2_project.project.id
  namespace_id = rancher2_namespace.project-namespace.name
  data = {
    AWS_URL               = base64encode(var.folio_embedded_s3 ? join("", ["https://", join(".", [join("-", [data.rancher2_cluster.cluster.name, rancher2_project.project.name, "minio"]), var.root_domain])]) : "https://s3.amazonaws.com")
    AWS_REGION            = base64encode(var.folio_embedded_s3 ? "" : var.aws_region)
    AWS_BUCKET            = base64encode(join("-", [data.rancher2_cluster.cluster.name, rancher2_project.project.name, "data-worker"]))
    AWS_ACCESS_KEY_ID     = base64encode(var.folio_embedded_s3 ? random_string.access_key[0].result : var.s3_access_key)
    AWS_SECRET_ACCESS_KEY = base64encode(var.folio_embedded_s3 ? random_password.secret_access_key[0].result : var.s3_secret_key)
  }
}

resource "rancher2_secret" "s3-credentials-data-export" {
  depends_on   = [rancher2_namespace.project-namespace]
  name         = "s3-credentials-data-export"
  project_id   = rancher2_project.project.id
  namespace_id = rancher2_namespace.project-namespace.name
  data = {
    AWS_URL               = base64encode(var.folio_embedded_s3 ? join("", ["https://", join(".", [join("-", [data.rancher2_cluster.cluster.name, rancher2_project.project.name, "minio"]), var.root_domain])]) : "https://s3.amazonaws.com")
    AWS_REGION            = base64encode(var.folio_embedded_s3 ? "" : var.aws_region)
    AWS_BUCKET            = base64encode(join("-", [data.rancher2_cluster.cluster.name, rancher2_project.project.name, "data-export"]))
    AWS_ACCESS_KEY_ID     = base64encode(var.folio_embedded_s3 ? random_string.access_key[0].result : var.s3_access_key)
    AWS_SECRET_ACCESS_KEY = base64encode(var.folio_embedded_s3 ? random_password.secret_access_key[0].result : var.s3_secret_key)
  }
}

# For edge* modules
resource "rancher2_secret" "ephemeral-properties" {
  depends_on   = [rancher2_namespace.project-namespace]
  name         = "ephemeral-properties"
  description  = "For edge modules"
  project_id   = rancher2_project.project.id
  namespace_id = rancher2_namespace.project-namespace.name
  data = {
    "ephemeral.properties" = base64encode(
      templatefile("${path.module}/resources/ephemeral.properties.tftpl",
        {
          tenant              = var.tenant_id,
          edge_tenants        = join(",", setsubtract(distinct(flatten([for i in local.edge-ephemeral-properties : keys(i)])), [var.tenant_id])),
          admin_user_username = var.admin_user.username,
          admin_user_password = var.admin_user.password,
          edge_properties     = local.edge-ephemeral-properties
        }
      )
    )
  }
}

# For edge* modules
resource "rancher2_secret" "edge-api-config" {
  depends_on   = [rancher2_namespace.project-namespace]
  name         = "apiconfiguration"
  description  = "For edge-orders module"
  project_id   = rancher2_project.project.id
  namespace_id = rancher2_namespace.project-namespace.name
  data = {
    "api_config" = filebase64("${path.module}/resources/api_configuration.json")
  }
}

/*resource "rancher2_secret" "s3-postgres-backups-credentials" {
  depends_on   = [rancher2_namespace.project-namespace]
  name         = "s3-postgres-backups-credentials"
  project_id   = rancher2_project.project.id
  namespace_id = rancher2_namespace.project-namespace.name
  data = {
    POSTGRES_USER                = base64encode(var.pg_username)
    POSTGRES_PASSWORD            = base64encode(var.pg_password)
    POSTGRES_DATABASE            = base64encode(var.pg_dbname)
    POSTGRES_HOST                = base64encode("pg-folio")
    S3_BACKUP_PATH               = base64encode("s3://folio-postgresql-backups")
    RANCHER_CLUSTER_PROJECT_NAME = base64encode(join("-", [data.rancher2_cluster.cluster.name, rancher2_project.project.name]))
    RELEASE_BRANCH               = base64encode(var.folio_release)
    AWS_BUCKET                   = base64encode(var.s3_postgres_backups_bucket_name)
    AWS_ACCESS_KEY_ID            = base64encode(var.s3_postgres_backups_access_key)
    AWS_SECRET_ACCESS_KEY        = base64encode(var.s3_postgres_backups_secret_key)
  }
}*/
