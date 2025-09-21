locals {
  helm_values = templatefile(
    "${path.module}/values.yaml.tmpl",
    {
      domain_name                           = var.domain_name,
      db_secret_name                        = var.db_secret_name
      image_repository                      = var.image_repository,
      image_tag                             = var.image_tag
      memory_limit                          = var.memory_limit
      memory_request                        = var.memory_request
      autoscaling_enabled                   = var.autoscaling_enabled
      autoscaling_min_replicas              = var.autoscaling_min_replicas
      autoscaling_max_replicas              = var.autoscaling_max_replicas
      autoscaling_target_memory_utilization = var.autoscaling_target_memory_utilization
      extra_java_opts                       = var.extra_java_opts
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

