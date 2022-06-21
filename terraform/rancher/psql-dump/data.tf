data "aws_eks_cluster" "cluster" {
  name = var.rancher_cluster_name
}

data "aws_eks_cluster_auth" "cluster" {
  name = var.rancher_cluster_name
}

data "rancher2_cluster" "cluster" {
  name = var.rancher_cluster_name
}

data "rancher2_project" "project" {
  cluster_id = data.rancher2_cluster.cluster.id
  name = var.rancher_project_name
}
