resource "random_string" "access_key" {
  count   = var.s3_embedded ? 1 : 0
  length  = 20
  lower   = false
  number  = false
  special = false
}

resource "random_password" "secret_access_key" {
  count            = var.s3_embedded ? 1 : 0
  length           = 40
  min_special      = 1
  override_special = "/"
}

resource "rancher2_app" "minio" {
  count            = var.s3_embedded ? 1 : 0
  project_id       = rancher2_project.this.id
  target_namespace = rancher2_namespace.this.name
  catalog_name     = "bitnami"
  name             = "minio"
  template_name    = "minio"
  template_version = "11.5.2"
  answers = {
    "auth.rootUser"     = random_string.access_key[0].result
    "auth.rootPassword" = random_password.secret_access_key[0].result
    "defaultBuckets" = join(",", [
      join("-", [data.rancher2_cluster.this.name, rancher2_project.this.name, "data-export"]),
      join("-", [data.rancher2_cluster.this.name, rancher2_project.this.name, "data-worker"]),
    ])
    "resources.limits.memory"                                                         = "1024Mi"
    "persistence.size"                                                                = "10Gi"
    "service.type"                                                                    = "NodePort"
    "ingress.enabled"                                                                 = "true"
    "ingress.annotations.kubernetes\\.io/ingress\\.class"                             = "alb"
    "ingress.annotations.alb\\.ingress\\.kubernetes\\.io/scheme"                      = "internet-facing"
    "ingress.annotations.alb\\.ingress\\.kubernetes\\.io/group\\.name"                = local.group_name
    "ingress.annotations.alb\\.ingress\\.kubernetes\\.io/listen-ports"                = "[{\"HTTPS\":443}]"
    "ingress.annotations.alb\\.ingress\\.kubernetes\\.io/success-codes"               = "200-399"
    "ingress.annotations.alb\\.ingress\\.kubernetes\\.io/healthcheck-path"            = "/"
    "ingress.annotations.alb\\.ingress\\.kubernetes\\.io/load-balancer-attributes"    = "idle_timeout.timeout_seconds=4000"
    "ingress.path"                                                                    = "/*"
    "ingress.hostname"                                                                = local.minio_console_url
    "apiIngress.enabled"                                                              = "true"
    "apiIngress.annotations.kubernetes\\.io/ingress\\.class"                          = "alb"
    "apiIngress.annotations.alb\\.ingress\\.kubernetes\\.io/scheme"                   = "internet-facing"
    "apiIngress.annotations.alb\\.ingress\\.kubernetes\\.io/group\\.name"             = local.group_name
    "apiIngress.annotations.alb\\.ingress\\.kubernetes\\.io/listen-ports"             = "[{\"HTTPS\":443}]"
    "apiIngress.annotations.alb\\.ingress\\.kubernetes\\.io/success-codes"            = "200-399"
    "apiIngress.annotations.alb\\.ingress\\.kubernetes\\.io/healthcheck-path"         = "/minio/health/live"
    "apiIngress.annotations.alb\\.ingress\\.kubernetes\\.io/load-balancer-attributes" = "idle_timeout.timeout_seconds=4000"
    "apiIngress.path"                                                                 = "/*"
    "apiIngress.hostname"                                                             = local.minio_url
  }
}
