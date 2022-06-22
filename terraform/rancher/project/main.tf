resource "rancher2_project" "project" {
  provider                  = rancher2
  name                      = terraform.workspace
  cluster_id                = data.rancher2_cluster.cluster.id
  enable_project_monitoring = false
  container_resource_limit {
    limits_memory   = "512Mi"
    requests_cpu    = "80m"
    requests_memory = "400Mi"
  }
}

# Create a new rancher2 Namespace assigned to cluster project
resource "rancher2_namespace" "project-namespace" {
  name        = rancher2_project.project.name
  project_id  = rancher2_project.project.id
  description = "Deafult namespace"
  container_resource_limit {
    limits_memory   = "512Mi"
    requests_cpu    = "90m"
    requests_memory = "400Mi"
  }
}

# Create a new rancher2 Project Role Template Binding
resource "rancher2_project_role_template_binding" "project-binding" {
  for_each           = toset(var.github_team_ids)
  name               = rancher2_project.project.name
  project_id         = rancher2_project.project.id
  role_template_id   = "project-member"
  group_principal_id = each.key
}

# Create a new Rancher2 Project Catalog for Folio charts
resource "rancher2_catalog" "folio-charts" {
  name       = join("-", [rancher2_project.project.name, "helmcharts"])
  url        = "https://folio-org.github.io/folio-helm"
  scope      = "project"
  version    = "helm_v3"
  project_id = rancher2_project.project.id
}

# Create a new Rancher2 Project Catalog for Influx charts
resource "rancher2_catalog" "influx" {
  name       = "influx"
  url        = "https://helm.influxdata.com/"
  scope      = "project"
  version    = "helm_v3"
  project_id = rancher2_project.project.id
}

# Create a new rancher2 Project Registry
resource "rancher2_registry" "folio-docker" {
  name        = "folio-docker"
  description = "docker.dev.folio.org registry"
  project_id  = rancher2_project.project.id
  registries {
    address  = "docker.dev.folio.org"
    username = var.folio_docker_registry_username
    password = var.folio_docker_registry_password
  }
}

# Create rancher2 OKAPI App in Project namespace
resource "rancher2_app" "folio-okapi" {
  project_id       = rancher2_project.project.id
  target_namespace = rancher2_namespace.project-namespace.name
  depends_on = [
    rancher2_secret.db-connect-modules, rancher2_catalog.folio-charts, rancher2_registry.folio-docker,
    rancher2_app.kafka, rancher2_app.elasticsearch, time_sleep.wait_for_db, module.rds
  ]
  catalog_name  = join(":", [element(split(":", rancher2_project.project.id), 1), rancher2_catalog.folio-charts.name])
  name          = "okapi"
  description   = "OKAPI app"
  force_upgrade = "true"
  template_name = "okapi"
  answers = {
    "image.repository"                                                             = join("/", [length(regexall(".*SNAPSHOT.*", var.okapi_version)) > 0 ? "folioci" : "folioorg", "okapi"])
    "image.tag"                                                                    = var.okapi_version
    "service.type"                                                                 = "NodePort"
    "ingress.enabled"                                                              = "true"
    "ingress.annotations.kubernetes\\.io/ingress\\.class"                          = "alb"
    "ingress.annotations.alb\\.ingress\\.kubernetes\\.io/scheme"                   = "internet-facing"
    "ingress.annotations.alb\\.ingress\\.kubernetes\\.io/group\\.name"             = join(".", [data.rancher2_cluster.cluster.name, rancher2_project.project.name])
    "ingress.annotations.alb\\.ingress\\.kubernetes\\.io/listen-ports"             = "[{\"HTTPS\":443}]"
    "ingress.annotations.alb\\.ingress\\.kubernetes\\.io/success-codes"            = "200-399"
    "ingress.annotations.alb\\.ingress\\.kubernetes\\.io/healthcheck-path"         = "/_/version"
    "ingress.annotations.alb\\.ingress\\.kubernetes\\.io/load-balancer-attributes" = "idle_timeout.timeout_seconds=4000"
    "ingress.hosts[0].paths[0]"                                                    = "/*"
    "ingress.hosts[0].host"                                                        = join(".", [join("-", [data.rancher2_cluster.cluster.name, rancher2_project.project.name, "okapi"]), var.root_domain])
    #"javaOptions"               = local.module_configs["okapi"].javaOptions
    "replicaCount"              = local.module_configs["okapi"].replicaCount
    "resources.requests.memory" = local.module_configs["okapi"].resources.requests.memory
    "resources.limits.memory"   = local.module_configs["okapi"].resources.limits.memory
  }
}

# Create a new rancher2 Stripes App in a default Project namespace
resource "rancher2_app" "folio-frontend" {
  project_id       = rancher2_project.project.id
  target_namespace = rancher2_namespace.project-namespace.name
  depends_on       = [rancher2_app.folio-backend]
  catalog_name     = join(":", [element(split(":", rancher2_project.project.id), 1), rancher2_catalog.folio-charts.name])
  name             = "platform-complete"
  description      = "Stripes UI"
  template_name    = "platform-complete"
  force_upgrade    = "true"
  answers = {
    "postJob.enabled"                                                   = "false"
    "image.tag"                                                         = var.stripes_image_tag
    "service.type"                                                      = "NodePort"
    "ingress.enabled"                                                   = "true"
    "ingress.annotations.kubernetes\\.io/ingress\\.class"               = "alb"
    "ingress.annotations.alb\\.ingress\\.kubernetes\\.io/scheme"        = "internet-facing"
    "ingress.annotations.alb\\.ingress\\.kubernetes\\.io/group\\.name"  = join(".", [data.rancher2_cluster.cluster.name, rancher2_project.project.name])
    "ingress.annotations.alb\\.ingress\\.kubernetes\\.io/listen-ports"  = "[{\"HTTPS\":443}]"
    "ingress.annotations.alb\\.ingress\\.kubernetes\\.io/success-codes" = "200-399"
    "ingress.hosts[0].paths[0]"                                         = "/*"
    "ingress.hosts[0].host"                                             = join(".", [join("-", [data.rancher2_cluster.cluster.name, rancher2_project.project.name]), var.root_domain])
  }
}

resource "rancher2_app" "folio-backend" {
  for_each         = local.backend-map
  project_id       = rancher2_project.project.id
  target_namespace = rancher2_namespace.project-namespace.name
  depends_on = [
    rancher2_secret.db-connect-modules, rancher2_catalog.folio-charts, rancher2_registry.folio-docker,
    rancher2_app.kafka, rancher2_app.elasticsearch, time_sleep.wait_for_db, module.rds, rancher2_app.folio-okapi
  ]
  catalog_name  = join(":", [element(split(":", rancher2_project.project.id), 1), rancher2_catalog.folio-charts.name])
  name          = each.key
  description   = join(" ", ["Folio app", each.key])
  force_upgrade = "true"
  template_name = each.key
  answers = {
    "postJob.enabled"  = "false"
    "image.repository" = join("/", [length(regexall(".*SNAPSHOT.*", each.value)) > 0 ? "folioci" : "folioorg", each.key])
    "image.tag"        = each.value
    #        "javaOptions"               = local.module_configs[(each.key)].javaOptions
    "replicaCount"              = local.module_configs[(each.key)].replicaCount
    "resources.requests.memory" = local.module_configs[(each.key)].resources.requests.memory
    "resources.limits.memory"   = local.module_configs[(each.key)].resources.limits.memory
  }
}

resource "rancher2_app" "folio-edge" {
  for_each         = local.edge-map
  project_id       = rancher2_project.project.id
  target_namespace = rancher2_namespace.project-namespace.name
  depends_on = [
    rancher2_secret.db-connect-modules, rancher2_catalog.folio-charts, rancher2_app.kafka, time_sleep.wait_for_db,
    module.rds, rancher2_app.folio-okapi
  ]
  catalog_name  = join(":", [element(split(":", rancher2_project.project.id), 1), rancher2_catalog.folio-charts.name])
  name          = each.key
  description   = join(" ", ["Folio app", each.key])
  force_upgrade = "true"
  template_name = each.key
  answers = {
    "image.repository"                                                     = join("/", [length(regexall(".*SNAPSHOT.*", each.value)) > 0 ? "folioci" : "folioorg", each.key])
    "image.tag"                                                            = each.value
    "service.type"                                                         = "NodePort"
    "ingress.enabled"                                                      = "true"
    "ingress.annotations.kubernetes\\.io/ingress\\.class"                  = "alb"
    "ingress.annotations.alb\\.ingress\\.kubernetes\\.io/scheme"           = "internet-facing"
    "ingress.annotations.alb\\.ingress\\.kubernetes\\.io/group\\.name"     = join(".", [data.rancher2_cluster.cluster.name, rancher2_project.project.name])
    "ingress.annotations.alb\\.ingress\\.kubernetes\\.io/listen-ports"     = "[{\"HTTPS\":443}]"
    "ingress.annotations.alb\\.ingress\\.kubernetes\\.io/success-codes"    = "200-399"
    "ingress.annotations.alb\\.ingress\\.kubernetes\\.io/healthcheck-path" = "/_/version"
    "ingress.hosts[0].paths[0]"                                            = "/${each.key}/*"
    "ingress.hosts[0].host"                                                = join(".", [join("-", [data.rancher2_cluster.cluster.name, rancher2_project.project.name, "okapi"]), var.root_domain])
    "javaOptions"                                                          = local.module_configs[(each.key)].javaOptions
    "replicaCount"                                                         = local.module_configs[(each.key)].replicaCount
    "resources.requests.memory"                                            = local.module_configs[(each.key)].resources.requests.memory
    "resources.limits.memory"                                              = local.module_configs[(each.key)].resources.limits.memory
  }
}

# TODO !!! Not tested !!!
# Create a new rancher2 Folio Edge-Sip2 App in a default Project namespace
resource "rancher2_app" "folio-edge-sip2" {
  for_each         = local.edge-sip2-map
  project_id       = rancher2_project.project.id
  target_namespace = rancher2_namespace.project-namespace.name
  depends_on = [
    rancher2_secret.db-connect-modules, rancher2_catalog.folio-charts, rancher2_app.kafka, time_sleep.wait_for_db,
    module.rds, rancher2_app.folio-okapi
  ]
  catalog_name  = join(":", [element(split(":", rancher2_project.project.id), 1), rancher2_catalog.folio-charts.name])
  name          = each.key
  description   = join(" ", ["Folio app", each.key])
  force_upgrade = "true"
  template_name = each.key
  answers = {
    "image.repository"                                                    = join("/", [length(regexall(".*SNAPSHOT.*", each.value)) > 0 ? "folioci" : "folioorg", each.key])
    "image.tag"                                                           = each.value
    "service.type"                                                        = "LoadBalancer"
    "service.beta.kubernetes.io/aws-load-balancer-type"                   = "nlb"
    "service.annotations.external-dns\\.alpha\\.kubernetes\\.io/hostname" = join(".", [join("-", [data.rancher2_cluster.cluster.name, rancher2_project.project.name, "sip2"]), var.root_domain])
    "javaOptions"                                                         = local.module_configs["edge-sip2"].javaOptions
    "replicaCount"                                                        = local.module_configs["edge-sip2"].replicaCount
    "resources.requests.memory"                                           = local.module_configs["edge-sip2"].resources.requests.memory
    "resources.limits.memory"                                             = local.module_configs["edge-sip2"].resources.limits.memory
  }
}

#TODO Think if always needed or only for perf
#Telegraf
#resource "rancher2_app" "telegraf-ds" {
#  project_id       = rancher2_project.project.id
#  target_namespace = rancher2_namespace.project-namespace.name
#  depends_on       = [rancher2_namespace.project-namespace, rancher2_app.folio-okapi, rancher2_app.folio-backend, rancher2_app.folio-backend-edge, rancher2_app.folio-backend-import-export]
#  catalog_name     = "influx"
#  name             = "telegraf-ds"
#  force_upgrade    = "true"
#  template_name    = "telegraf-ds"
#  answers = {
#    "config.inputs[0].kubernetes.bearer_token"         = "/var/run/secrets/kubernetes.io/serviceaccount/token"
#    "config.inputs[0].kubernetes.insecure_skip_verify" = "true"
#    "config.inputs[0].kubernetes.url"                  = "http://$HOSTNAME:10255"
#    "config.outputs[0].influxdb.urls[0]"               = "${var.carrier_url}:8086"
#  }
#}
