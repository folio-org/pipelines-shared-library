locals {
  helm_values = templatefile(
    "${path.module}/values.yaml.tmpl",
    {
      domain_name      = var.domain_name,
      db_secret_name   = var.db_secret_name
      image_repository = var.image_repository,
      image_tag        = var.image_tag
    }
  )
}

resource "helm_release" "far_mgr_app" {
  name       = "far-mgr-applications"
  repository = "https://repository.folio.org/repository/folio-helm-v2"
  chart      = "mgr-applications"
  version    = var.chart_version
  namespace  = var.namespace_id
  values     = [local.helm_values]

  force_update = true
  replace      = true
  atomic       = true

  depends_on = [
    var.dependencies
  ]
}

