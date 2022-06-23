# Create rancher2 OKAPI app in Project namespace
resource "rancher2_app" "okapi" {
  depends_on       = [time_sleep.wait_for_db, rancher2_app.kafka, rancher2_app.elasticsearch, rancher2_app.minio, module.rds, aws_msk_cluster.this, module.aws_es]
  project_id       = rancher2_project.this.id
  target_namespace = rancher2_namespace.this.name
  catalog_name     = local.folio_helm_catalog_name
  name             = "okapi"
  description      = "OKAPI app"
  template_name    = "okapi"
  force_upgrade    = "true"
  answers = {
    "image.repository"                                                             = join("/", [length(regexall(".*SNAPSHOT.*", var.okapi_version)) > 0 ? "folioci" : "folioorg", "okapi"])
    "image.tag"                                                                    = var.okapi_version
    "service.type"                                                                 = "NodePort"
    "ingress.enabled"                                                              = "true"
    "ingress.annotations.kubernetes\\.io/ingress\\.class"                          = "alb"
    "ingress.annotations.alb\\.ingress\\.kubernetes\\.io/scheme"                   = "internet-facing"
    "ingress.annotations.alb\\.ingress\\.kubernetes\\.io/group\\.name"             = local.group_name
    "ingress.annotations.alb\\.ingress\\.kubernetes\\.io/listen-ports"             = "[{\"HTTPS\":443}]"
    "ingress.annotations.alb\\.ingress\\.kubernetes\\.io/success-codes"            = "200-399"
    "ingress.annotations.alb\\.ingress\\.kubernetes\\.io/healthcheck-path"         = "/_/version"
    "ingress.annotations.alb\\.ingress\\.kubernetes\\.io/load-balancer-attributes" = "idle_timeout.timeout_seconds=4000"
    "ingress.hosts[0].paths[0]"                                                    = "/*"
    "ingress.hosts[0].host"                                                        = local.okapi_url
    "postJob.enabled"                                                              = "false"
    "javaOptions"                                                                  = try(local.helm_configs["okapi"]["javaOptions"])
    "replicaCount"                                                                 = try(local.helm_configs["okapi"]["replicaCount"])
    "resources.requests.memory"                                                    = try(local.helm_configs["okapi"]["resources"]["requests"]["memory"])
    "resources.limits.memory"                                                      = try(local.helm_configs["okapi"]["resources"]["limits"]["memory"])
  }
}
