data "aws_eks_cluster" "this" {
  name = var.cluster_name
}

# Getting the EKS cluster data from the Rancher cluster name.
data "rancher2_cluster_v2" "this" {
  name = var.cluster_name
}


locals {
  cluster_id = var.cluster_name == "rancher" ? "local" : data.rancher2_cluster_v2.this.id
}