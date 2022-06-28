resource "rancher2_project" "this" {
  provider                  = rancher2
  name                      = var.rancher_project_name
  cluster_id                = data.rancher2_cluster.this.id
  enable_project_monitoring = false
  container_resource_limit {
    limits_memory   = "512Mi"
    requests_cpu    = "80m"
    requests_memory = "400Mi"
  }
}

# Create a new rancher2 Project Role Template Binding
resource "rancher2_project_role_template_binding" "this" {
  for_each           = toset(var.github_team_ids)
  name               = rancher2_project.this.name
  project_id         = rancher2_project.this.id
  role_template_id   = "project-member"
  group_principal_id = each.key
}

# Create a new rancher2 Namespace assigned to cluster project
resource "rancher2_namespace" "this" {
  name        = rancher2_project.this.name
  project_id  = rancher2_project.this.id
  description = "Project default namespace"
  container_resource_limit {
    limits_memory   = "512Mi"
    requests_cpu    = "80m"
    requests_memory = "400Mi"
  }
}

# Create a new Rancher2 Project Catalog for Folio charts
resource "rancher2_catalog" "folio-charts" {
  name       = join("-", [rancher2_project.this.name, "helmcharts"])
  url        = "https://folio-org.github.io/folio-helm"
  scope      = "project"
  version    = "helm_v3"
  project_id = rancher2_project.this.id
}

# Create a new rancher2 Project Registry
resource "rancher2_registry" "folio-docker" {
  name        = "folio-docker"
  description = "docker.dev.folio.org registry"
  project_id  = rancher2_project.this.id
  registries {
    address  = "docker.dev.folio.org"
    username = var.folio_docker_registry_username
    password = var.folio_docker_registry_password
  }
}

#TODO Check if always needed or only for perf

# Create a new Rancher2 Project Catalog for Influx charts
#resource "rancher2_catalog" "influx" {
#  name       = "influx"
#  url        = "https://helm.influxdata.com/"
#  scope      = "project"
#  version    = "helm_v3"
#  project_id = rancher2_project.this.id
#}

#Telegraf
#resource "rancher2_app" "telegraf-ds" {
#  project_id       = rancher2_project.this.id
#  target_namespace = rancher2_namespace.this.name
#  depends_on       = [rancher2_namespace.this, rancher2_app.folio-okapi, rancher2_app.folio-backend, rancher2_app.folio-backend-edge, rancher2_app.folio-backend-import-export]
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
