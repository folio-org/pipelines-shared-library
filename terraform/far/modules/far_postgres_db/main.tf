data "aws_eks_cluster" "this" {
  name = var.cluster_name
}

data "aws_subnet" "this" {
  id = tolist(data.aws_eks_cluster.this.vpc_config[0].subnet_ids)[0]
}

locals {
  helm_values = templatefile(
    "${path.module}/values.yaml.tmpl",
    {
      db_user     = local.secret_data.DB_USERNAME,
      db_password = local.secret_data.DB_PASSWORD,
      db_name     = local.secret_data.DB_DATABASE,
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
    DB_HOST         = local.secret_data.DB_HOST
    DB_PORT         = local.secret_data.DB_PORT
    DB_DATABASE     = local.secret_data.DB_DATABASE
    DB_USERNAME     = local.secret_data.DB_USERNAME
    DB_PASSWORD     = local.secret_data.DB_PASSWORD
    DB_MAXPOOLSIZE  = local.secret_data.DB_MAXPOOLSIZE
    DB_CHARSET      = local.secret_data.DB_CHARSET
    DB_QUERYTIMEOUT = local.secret_data.DB_QUERYTIMEOUT
  }

  lifecycle {
    ignore_changes = [data]
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
