resource "random_string" "access_key" {
  count   = var.folio_embedded_s3 ? 1 : 0
  length  = 20
  lower   = false
  number  = false
  special = false
}

resource "random_password" "secret_access_key" {
  count            = var.folio_embedded_s3 ? 1 : 0
  length           = 40
  min_special      = 1
  override_special = "/"
}

resource "rancher2_app" "minio" {
  count            = var.folio_embedded_s3 ? 1 : 0
  project_id       = rancher2_project.project.id
  target_namespace = rancher2_namespace.project-namespace.name
  catalog_name     = "bitnami"
  name             = "minio"
  template_name    = "minio"
  template_version = "11.5.2"
  answers = {
    "image.tag"         = "2022.5.8"
    "auth.rootUser"     = random_string.access_key[0].result
    "auth.rootPassword" = random_password.secret_access_key[0].result
    "defaultBuckets" = join(",", [
      join("-", [data.rancher2_cluster.cluster.name, rancher2_project.project.name, "data-export"]),
      join("-", [data.rancher2_cluster.cluster.name, rancher2_project.project.name, "data-worker"]),
    ])
    "resources.limits.memory"                                                      = "1024Mi"
    "persistence.size"                                                             = "10Gi"
    "service.type"                                                                 = "NodePort"
    "ingress.enabled"                                                              = "true"
    "ingress.annotations.kubernetes\\.io/ingress\\.class"                          = "alb"
    "ingress.annotations.alb\\.ingress\\.kubernetes\\.io/scheme"                   = "internet-facing"
    "ingress.annotations.alb\\.ingress\\.kubernetes\\.io/group\\.name"             = join(".", [data.rancher2_cluster.cluster.name, rancher2_project.project.name])
    "ingress.annotations.alb\\.ingress\\.kubernetes\\.io/listen-ports"             = "[{\"HTTPS\":443}]"
    "ingress.annotations.alb\\.ingress\\.kubernetes\\.io/success-codes"            = "200-399"
    "ingress.annotations.alb\\.ingress\\.kubernetes\\.io/healthcheck-path"         = "/"
    "ingress.annotations.alb\\.ingress\\.kubernetes\\.io/load-balancer-attributes" = "idle_timeout.timeout_seconds=4000"
    "ingress.path"                                                                 = "/*"
    "ingress.hostname"                                                             = join(".", [join("-", [data.rancher2_cluster.cluster.name, rancher2_project.project.name, "minio"]), var.root_domain])
  }
}
