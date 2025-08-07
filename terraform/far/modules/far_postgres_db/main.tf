data "aws_eks_cluster" "this" {
  name = var.cluster_name
}

data "aws_subnet" "this" {
  id = tolist(data.aws_eks_cluster.this.vpc_config[0].subnet_ids)[0]
}

resource "random_password" "postgres" {
  length           = var.password_length
  special          = true
  override_special = var.password_override_special

  keepers = {
    namespace = var.namespace_name
  }
}

locals {
  helm_values = templatefile(
    "${path.module}/values.yaml.tmpl",
    {
      db_user     = var.db_username,
      db_password = random_password.postgres.result,
      db_name     = var.db_name,
      pvc_name    = kubernetes_persistent_volume_claim.postgres.metadata[0].name
    }
  )
  postgresql_service_name = "${var.release_name}-postgresql"
}

resource "rancher2_secret_v2" "postgres_credentials" {
  name       = "${var.namespace_name}-postgres-credentials"
  cluster_id = var.cluster_id
  namespace  = var.namespace_id

  data = {
    DB_HOST         = local.postgresql_service_name
    DB_PORT         = tostring(var.db_port)
    DB_DATABASE     = var.db_name
    DB_USERNAME     = var.db_username
    DB_PASSWORD     = random_password.postgres.result
    DB_MAXPOOLSIZE  = var.db_max_connections
    DB_CHARSET      = var.db_charset
    DB_QUERYTIMEOUT = var.db_query_timeout
  }

  lifecycle {
    ignore_changes = [data]
  }
}

resource "aws_secretsmanager_secret" "postgres_credentials" {
  name        = "/${var.cluster_name}/${var.namespace_name}/postgres-credentials"
  description = "PostgreSQL credentials for ${var.namespace_name} in ${var.cluster_name} cluster"

  tags = merge(
    var.tags,
    {
      Name        = "${var.cluster_name}-${var.namespace_name}-postgres-credentials"
      Application = "MGR-FAR"
      Terraform   = "true"
    }
  )

  lifecycle {
    prevent_destroy = true
  }
}

resource "aws_secretsmanager_secret_version" "postgres_credentials" {
  secret_id = aws_secretsmanager_secret.postgres_credentials.id
  secret_string = jsonencode({
    DB_HOST         = local.postgresql_service_name
    DB_PORT         = tostring(var.db_port)
    DB_DATABASE     = var.db_name
    DB_USERNAME     = var.db_username
    DB_PASSWORD     = random_password.postgres.result
    DB_MAXPOOLSIZE  = var.db_max_connections
    DB_CHARSET      = var.db_charset
    DB_QUERYTIMEOUT = var.db_query_timeout
  })

  lifecycle {
    prevent_destroy = true
  }
}

resource "helm_release" "postgres" {
  name            = var.release_name
  namespace       = var.namespace_name
  repository      = var.helm_repository
  chart           = var.helm_chart
  version         = var.chart_version
  cleanup_on_fail = true
  values          = [local.helm_values]

  atomic       = true
  timeout      = var.helm_timeout
  wait         = true
  force_update = true

  depends_on = [
    kubernetes_persistent_volume_claim.postgres
  ]
}