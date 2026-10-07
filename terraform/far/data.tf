data "aws_eks_cluster" "this" {
  name = var.cluster_name
}

# Getting the EKS cluster data from the Rancher cluster name.
data "rancher2_cluster" "this" {
  count = var.rancher_cluster_id == null && var.cluster_name != "rancher" ? 1 : 0
  name  = var.cluster_name
}

locals {
  cluster_id = var.rancher_cluster_id != null ? var.rancher_cluster_id : (var.cluster_name == "rancher" ? "local" : data.rancher2_cluster.this[0].id)
}
