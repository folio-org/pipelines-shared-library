# Create a new rancher2 Folio ElasticSearch App in a default Project namespace
resource "rancher2_app" "elasticsearch" {
  count = contains(keys(local.backend-map), "mod-search") && var.folio_embedded_es ? 1 : 0
  depends_on = [
    rancher2_secret.db-connect-modules, rancher2_catalog.folio-charts, rancher2_app.kafka, time_sleep.wait_for_db
  ]
  catalog_name     = "bitnami"
  name             = "elasticsearch"
  description      = "Elasticsearch for mod-search"
  force_upgrade    = "true"
  project_id       = rancher2_project.project.id
  template_name    = "elasticsearch"
  target_namespace = rancher2_namespace.project-namespace.name
  template_version = "17.9.29"
  answers = {
    "image.debug"                                    = "true"
    "global.coordinating.name"                       = rancher2_project.project.name
    "coordinating.replicas"                          = "1"
    "coordinating.resources.limits.cpu"              = "512m"
    "coordinating.resources.limits.memory"           = "2048Mi"
    "coordinating.resources.requests.cpu"            = "256m"
    "coordinating.resources.requests.memory"         = "1024Mi"
    "coordinating.livenessProbe.initialDelaySeconds" = "180"
    "data.replicas"                                  = "1"
    "data.resources.limits.cpu"                      = "512m"
    "data.resources.limits.memory"                   = "2048Mi"
    "data.resources.requests.cpu"                    = "256m"
    "data.resources.requests.memory"                 = "1024Mi"
    "data.livenessProbe.initialDelaySeconds"         = "180"
    "master.replicas"                                = "1"
    "master.resources.limits.cpu"                    = "512m"
    "master.resources.limits.memory"                 = "2048Mi"
    "master.resources.requests.cpu"                  = "256m"
    "master.resources.requests.memory"               = "1048Mi"
    "master.livenessProbe.initialDelaySeconds"       = "180"
    "plugins"                                        = "analysis-icu, analysis-kuromoji, analysis-smartcn, analysis-nori, analysis-phonetic"
  }
}
# TODO Add opensearch provisioning
