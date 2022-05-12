# Rancher2 Project App Kafka
resource "rancher2_app" "kafka" {
  count            = var.folio_embedded_kafka ? 1 : 0
  project_id       = rancher2_project.project.id
  target_namespace = rancher2_namespace.project-namespace.name
  catalog_name     = "bitnami"
  name             = "kafka"
  template_name    = "kafka"
  force_upgrade    = "true"
  answers = {
    "image.tag"                  = var.kafka_version
    "global.storageClass"        = "gp2"
    "metrics.kafka.enabled"      = "false"
    "persistence.enabled"        = "true"
    "persistence.size"           = "10Gi"
    "persistence.storageClass"   = "gp2"
    "resources.limits.cpu"       = "500m"
    "resources.limits.memory"    = "1200Mi"
    "resources.requests.cpu"     = "250m"
    "resources.requests.memory"  = "1100Mi" // originally 256Mi
    "zookeeper.enabled"          = "true"
    "zookeeper.persistence.size" = "5Gi"
    "livenessProbe.enabled"      = "false"
    "readinessProbe.enabled"     = "false"
  }
}
# TODO Add MSK provisioning support
