# Create a new rancher2 imported Cluster
resource "rancher2_cluster" "this" {
  count                     = var.register_in_rancher ? 1 : 0
  depends_on                = [module.eks_cluster]
  name                      = terraform.workspace
  description               = "Folio rancher2 imported cluster"
  enable_cluster_monitoring = false
}

data "http" "cattle" {
  count = var.register_in_rancher ? 1 : 0
  url   = rancher2_cluster.this[0].cluster_registration_token[0].manifest_url
}

locals {
  cattle-list = var.register_in_rancher ? compact(split("---", trimspace(data.http.cattle[0].body))) : [""]
}

resource "kubectl_manifest" "cattle" {
  count     = var.register_in_rancher ? 9 : 0
  yaml_body = local.cattle-list[count.index]
}

# Create a new rancher2 Cluster Sync
resource "rancher2_cluster_sync" "folio-imported" {
  count         = var.register_in_rancher ? 1 : 0
  depends_on    = [kubectl_manifest.cattle]
  cluster_id    = rancher2_cluster.this[0].id
  state_confirm = 3
}
