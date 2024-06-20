#Creating a cloud credential in Rancher.
resource "rancher2_cloud_credential" "aws" {
  depends_on  = [helm_release.alb_controller, helm_release.aws_cluster_autoscaler, helm_release.external_dns]
  count       = var.register_in_rancher ? 1 : 0
  name        = module.eks_cluster.cluster_name
  description = "AWS EKS Cluster"
  amazonec2_credential_config {
    access_key = var.aws_access_key_id
    secret_key = var.aws_secret_access_key
  }
}

#Creating a Rancher2 cluster object.
resource "rancher2_cluster" "this" {
  count       = var.register_in_rancher ? 1 : 0
  name        = module.eks_cluster.cluster_name
  description = "Terraform EKS Cluster"
  eks_config_v2 {
    cloud_credential_id = rancher2_cloud_credential.aws[0].id
    name                = module.eks_cluster.cluster_name
    region              = var.aws_region
    imported            = true
  }
}

#Syncing the cluster with Rancher.
resource "rancher2_cluster_sync" "this" {
  count      = var.register_in_rancher ? 1 : 0
  cluster_id = rancher2_cluster.this[0].id
  timeouts {
    create = "60m"
    update = "60m"
    delete = "60m"
  }
}
