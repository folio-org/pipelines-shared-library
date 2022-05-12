# Create a new rancher2 imported Cluster
resource "rancher2_cluster" "folio-imported" {
  depends_on                = [module.eks_cluster]
  name                      = terraform.workspace
  description               = "Folio rancher2 imported cluster"
  enable_cluster_monitoring = false
}

data "http" "cattle" {
  url = rancher2_cluster.folio-imported.cluster_registration_token.0.manifest_url
}

locals {
  cattle-list = compact(split("---", trimspace(data.http.cattle.body)))
}

resource "kubectl_manifest" "cattle" {
  depends_on = [rancher2_cluster.folio-imported]
  count      = 9
  yaml_body  = local.cattle-list[count.index]
}

# Create a new rancher2 Cluster Sync
resource "rancher2_cluster_sync" "folio-imported" {
  depends_on    = [kubectl_manifest.cattle]
  cluster_id    = rancher2_cluster.folio-imported.id
  state_confirm = 3
}
