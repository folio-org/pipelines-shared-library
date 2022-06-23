# Create rancher2 Edge apps in Project namespace
resource "rancher2_app" "edge" {
  for_each         = local.edge-map
  project_id       = rancher2_project.this.id
  target_namespace = rancher2_namespace.this.name
  catalog_name     = local.folio_helm_catalog_name
  name             = each.key
  description      = join(" ", ["Folio app", each.key])
  template_name    = each.key
  force_upgrade    = "true"
  answers = {
    "image.repository"                                                     = join("/", [length(regexall(".*SNAPSHOT.*", each.value)) > 0 ? "folioci" : "folioorg", each.key])
    "image.tag"                                                            = each.value
    "service.type"                                                         = "NodePort"
    "ingress.enabled"                                                      = "true"
    "ingress.annotations.kubernetes\\.io/ingress\\.class"                  = "alb"
    "ingress.annotations.alb\\.ingress\\.kubernetes\\.io/scheme"           = "internet-facing"
    "ingress.annotations.alb\\.ingress\\.kubernetes\\.io/group\\.name"     = local.group_name
    "ingress.annotations.alb\\.ingress\\.kubernetes\\.io/listen-ports"     = "[{\"HTTPS\":443}]"
    "ingress.annotations.alb\\.ingress\\.kubernetes\\.io/success-codes"    = "200-399"
    "ingress.annotations.alb\\.ingress\\.kubernetes\\.io/healthcheck-path" = "/_/version"
    "ingress.hosts[0].paths[0]"                                            = "/${each.key}/*"
    "ingress.hosts[0].host"                                                = join(".", [join("-", [data.rancher2_cluster.this.name, rancher2_project.this.name, "okapi"]), var.root_domain])
    "javaOptions"                                                          = try(local.helm_configs[each.key]["javaOptions"])
    "replicaCount"                                                         = try(local.helm_configs[each.key]["replicaCount"])
    "resources.requests.memory"                                            = try(local.helm_configs[each.key]["resources"]["requests"]["memory"])
    "resources.limits.memory"                                              = try(local.helm_configs[each.key]["resources"]["limits"]["memory"])
  }
}

# Create rancher2 Edge-sip2 app in a default Project namespace
resource "rancher2_app" "edge-sip2" {
  depends_on       = [rancher2_app.okapi]
  for_each         = local.edge-sip2-map
  project_id       = rancher2_project.this.id
  target_namespace = rancher2_namespace.this.name
  catalog_name     = local.folio_helm_catalog_name
  name             = each.key
  description      = join(" ", ["Folio app", each.key])
  template_name    = each.key
  force_upgrade    = "true"
  answers = {
    "image.repository"                                                    = join("/", [length(regexall(".*SNAPSHOT.*", each.value)) > 0 ? "folioci" : "folioorg", each.key])
    "image.tag"                                                           = each.value
    "service.type"                                                        = "LoadBalancer"
    "service.beta.kubernetes.io/aws-load-balancer-type"                   = "nlb"
    "service.annotations.external-dns\\.alpha\\.kubernetes\\.io/hostname" = join(".", [join("-", [data.rancher2_cluster.this.name, rancher2_project.this.name, "sip2"]), var.root_domain])
    "javaOptions"                                                         = try(local.helm_configs[each.key]["javaOptions"])
    "replicaCount"                                                        = try(local.helm_configs[each.key]["replicaCount"])
    "resources.requests.memory"                                           = try(local.helm_configs[each.key]["resources"]["requests"]["memory"])
    "resources.limits.memory"                                             = try(local.helm_configs[each.key]["resources"]["limits"]["memory"])
  }
}
