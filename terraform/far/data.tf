data "aws_eks_cluster" "this" {
  name = var.cluster_name
}

# Getting the EKS cluster data from the Rancher cluster name.
data "rancher2_cluster" "this" {
  name = var.cluster_name
}