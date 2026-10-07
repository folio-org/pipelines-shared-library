locals {
  builtin_ingress_annotations = concat([
    { key = "alb.ingress.kubernetes.io/group.name", value = "rancher" },
    { key = "alb.ingress.kubernetes.io/target-type", value = "ip" },
    { key = "alb.ingress.kubernetes.io/target-group-attributes", value = "deregistration_delay.timeout_seconds=30" },
    { key = "alb.ingress.kubernetes.io/healthcheck-path", value = "/admin/health" },
    { key = "alb.ingress.kubernetes.io/listen-ports", value = "'[{\"HTTP\": 80}, {\"HTTPS\": 443}]'" },
    { key = "alb.ingress.kubernetes.io/ssl-redirect", value = "\"443\"" },
    { key = "alb.ingress.kubernetes.io/load-balancer-attributes", value = "idle_timeout.timeout_seconds=4000" },
    { key = "alb.ingress.kubernetes.io/scheme", value = "internet-facing" },
    { key = "alb.ingress.kubernetes.io/success-codes", value = "200-399" },
    { key = "kubernetes.io/ingress.class", value = "alb" },
  ], var.certificate_arn != "" ? [{ key = "alb.ingress.kubernetes.io/certificate-arn", value = var.certificate_arn }] : [])

  ingress_annotations = concat(
    [for a in local.builtin_ingress_annotations : {
      key   = a.key
      value = contains(keys(var.ingress_extra_annotations), a.key) ? jsonencode(var.ingress_extra_annotations[a.key]) : a.value
    }],
    [for k, v in var.ingress_extra_annotations : { key = k, value = jsonencode(v) } if !contains(local.builtin_ingress_annotations[*].key, k)]
  )

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
      ingress_annotations                   = local.ingress_annotations
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

