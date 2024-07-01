# Folio helm charts catalog
resource "rancher2_catalog_v2" "folio-helm" {
  count      = var.register_in_rancher ? 1 : 0
  cluster_id = rancher2_cluster_sync.this[0].id
  name       = "folio-helm"
  url        = "https://folio-org.github.io/folio-helm"
}

# Folio service helm charts catalog
resource "rancher2_catalog_v2" "folio-helm-service" {
  count      = var.register_in_rancher ? 1 : 0
  cluster_id = rancher2_cluster_sync.this[0].id
  name       = "folio-helm-service"
  url        = "https://repository.folio.org/repository/helm-hosted"
}

# OpenSearch helm charts catalog
resource "rancher2_catalog_v2" "opensearch" {
  count      = var.register_in_rancher ? 1 : 0
  cluster_id = rancher2_cluster_sync.this[0].id
  name       = "opensearch"
  url        = "https://opensearch-project.github.io/helm-charts"
}

# Bitnami helm charts catalog
resource "rancher2_catalog_v2" "bitnami" {
  count      = var.register_in_rancher ? 1 : 0
  cluster_id = rancher2_cluster_sync.this[0].id
  name       = "bitnami"
  url        = "https://repository.folio.org/repository/helm-bitnami-proxy"
}

# AWS ebs csi driver charts catalog
resource "rancher2_catalog_v2" "aws-ebs-csi-driver" {
  count      = var.register_in_rancher ? 1 : 0
  cluster_id = rancher2_cluster_sync.this[0].id
  name       = "aws-ebs-csi-driver"
  url        = "https://kubernetes-sigs.github.io/aws-ebs-csi-driver"
}

# Helm charts catalog
resource "rancher2_catalog_v2" "helm" {
  count      = var.register_in_rancher ? 1 : 0
  cluster_id = rancher2_cluster_sync.this[0].id
  name       = "helm"
  url        = "https://charts.helm.sh/stable"
}

# Helm incubator charts catalog
resource "rancher2_catalog_v2" "helm-incubator" {
  count      = var.register_in_rancher ? 1 : 0
  cluster_id = rancher2_cluster_sync.this[0].id
  name       = "helm-incubator"
  url        = "https://charts.helm.sh/incubator"
}

# Influx charts catalog
resource "rancher2_catalog_v2" "influx" {
  count      = var.register_in_rancher ? 1 : 0
  cluster_id = rancher2_cluster_sync.this[0].id
  name       = "influx"
  url        = "https://helm.influxdata.com"
}

# Grafana charts catalog
resource "rancher2_catalog_v2" "grafana" {
  count      = var.register_in_rancher ? 1 : 0
  cluster_id = rancher2_cluster_sync.this[0].id
  name       = "grafana"
  url        = "https://grafana.github.io/helm-charts"
}

# Runix charts catalog
resource "rancher2_catalog_v2" "runix" {
  count      = var.register_in_rancher ? 1 : 0
  cluster_id = rancher2_cluster_sync.this[0].id
  name       = "runix"
  url        = "https://helm.runix.net"
}

# Kubecost charts catalog
resource "rancher2_catalog_v2" "kubecost" {
  count      = var.register_in_rancher ? 1 : 0
  cluster_id = rancher2_cluster_sync.this[0].id
  name       = "cost-analyzer"
  url        = "https://kubecost.github.io/cost-analyzer"
}

# Provectus charts catalog, for Kafka_ui
resource "rancher2_catalog_v2" "provectus" {
  count      = var.register_in_rancher ? 1 : 0
  cluster_id = rancher2_cluster_sync.this[0].id
  name       = "provectus"
  url        = "https://provectus.github.io/kafka-ui-charts"
}

# Prometheus Community charts catalog, for prometheus
resource "rancher2_catalog_v2" "prometheus-community" {
  count      = var.register_in_rancher ? 1 : 0
  cluster_id = rancher2_cluster_sync.this[0].id
  name       = "prometheus-community"
  url        = "https://prometheus-community.github.io/helm-charts"
}

# Kubernetes-sigs chart catalog, for metrics-server
resource "rancher2_catalog_v2" "metrics-server" {
  count      = var.register_in_rancher ? 1 : 0
  cluster_id = rancher2_cluster_sync.this[0].id
  name       = "metrics-server"
  url        = "https://kubernetes-sigs.github.io/metrics-server"
}

# Sorry Cypress charts catalog
resource "rancher2_catalog_v2" "sorry-cypress" {
  count      = var.register_in_rancher ? 1 : 0
  cluster_id = rancher2_cluster_sync.this[0].id
  name       = "sorry-cypress"
  url        = "https://sorry-cypress.github.io/charts"
}

# MockServer charts catalog
resource "rancher2_catalog_v2" "mock-server" {
  count      = var.register_in_rancher ? 1 : 0
  cluster_id = rancher2_cluster_sync.this[0].id
  name       = "mock-server"
  url        = "http://www.mock-server.com"
}
