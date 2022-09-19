# Folio helm charts catalog
resource "rancher2_catalog_v2" "folio-helm" {
  depends_on = [time_sleep.wait_120_seconds]
  count      = var.register_in_rancher ? 1 : 0
  cluster_id = rancher2_cluster_sync.this[0].cluster_id
  name       = "folio-helm"
  url        = "https://folio-org.github.io/folio-helm"
}

# Folio service helm charts catalog
resource "rancher2_catalog_v2" "folio-helm-service" {
  depends_on = [time_sleep.wait_120_seconds]
  count      = var.register_in_rancher ? 1 : 0
  cluster_id = rancher2_cluster_sync.this[0].cluster_id
  name       = "folio-helm-service"
  url        = "https://repository.folio.org/repository/helm-hosted/"
}

# OpenSearch helm charts catalog
resource "rancher2_catalog_v2" "opensearch" {
  depends_on = [time_sleep.wait_120_seconds]
  count      = var.register_in_rancher ? 1 : 0
  cluster_id = rancher2_cluster_sync.this[0].cluster_id
  name       = "opensearch"
  url        = "https://opensearch-project.github.io/helm-charts"
}

# Bitnami helm charts catalog
resource "rancher2_catalog_v2" "bitnami" {
  depends_on = [time_sleep.wait_120_seconds]
  count      = var.register_in_rancher ? 1 : 0
  cluster_id = rancher2_cluster_sync.this[0].cluster_id
  name       = "bitnami"
  url        = "https://repository.folio.org/repository/helm-bitnami-proxy/"
}

# AWS ebs csi driver charts catalog
resource "rancher2_catalog_v2" "aws-ebs-csi-driver" {
  depends_on = [time_sleep.wait_120_seconds]
  count      = var.register_in_rancher ? 1 : 0
  cluster_id = rancher2_cluster_sync.this[0].cluster_id
  name       = "aws-ebs-csi-driver"
  url        = "https://kubernetes-sigs.github.io/aws-ebs-csi-driver"
}

# Helm charts catalog
resource "rancher2_catalog_v2" "helm" {
  depends_on = [time_sleep.wait_120_seconds]
  count      = var.register_in_rancher ? 1 : 0
  cluster_id = rancher2_cluster_sync.this[0].cluster_id
  name       = "helm"
  url        = "https://charts.helm.sh/stable"
}

# Helm incubator charts catalog
resource "rancher2_catalog_v2" "helm-incubator" {
  depends_on = [time_sleep.wait_120_seconds]
  count      = var.register_in_rancher ? 1 : 0
  cluster_id = rancher2_cluster_sync.this[0].cluster_id
  name       = "helm-incubator"
  url        = "https://charts.helm.sh/incubator"
}

# Influx charts catalog
resource "rancher2_catalog_v2" "influx" {
  depends_on = [time_sleep.wait_120_seconds]
  count      = var.register_in_rancher ? 1 : 0
  cluster_id = rancher2_cluster_sync.this[0].cluster_id
  name       = "influx"
  url        = "https://helm.influxdata.com/"
}

# Grafana charts catalog
resource "rancher2_catalog_v2" "grafana" {
  depends_on = [time_sleep.wait_120_seconds]
  count      = var.register_in_rancher ? 1 : 0
  cluster_id = rancher2_cluster_sync.this[0].cluster_id
  name       = "grafana"
  url        = "https://grafana.github.io/helm-charts"
}

# Runix charts catalog
resource "rancher2_catalog_v2" "runix" {
  depends_on = [time_sleep.wait_120_seconds]
  count      = var.register_in_rancher ? 1 : 0
  cluster_id = rancher2_cluster_sync.this[0].cluster_id
  name       = "runix"
  url        = "https://helm.runix.net"
}
