#Creating a cloud credential in Rancher.
resource "rancher2_cloud_credential" "aws" {
  depends_on  = [helm_release.alb_controller, helm_release.aws_cluster_autoscaler, helm_release.aws_ebs_csi, helm_release.external_dns]
  count       = var.register_in_rancher ? 1 : 0
  name        = module.eks_cluster.cluster_id
  description = "AWS EKS Cluster"
  amazonec2_credential_config {
    access_key = var.aws_access_key_id
    secret_key = var.aws_secret_access_key
  }
}

#Creating a Rancher2 cluster object.
resource "rancher2_cluster" "this" {
  count       = var.register_in_rancher ? 1 : 0
  name        = module.eks_cluster.cluster_id
  description = "Terraform EKS Cluster"
  eks_config_v2 {
    cloud_credential_id = rancher2_cloud_credential.aws[0].id
    name                = module.eks_cluster.cluster_id
    region              = var.aws_region
    imported            = true
  }
  timeouts {
    create = "60m"
  }
}

#Syncing the cluster with Rancher.
resource "rancher2_cluster_sync" "this" {
  count      = var.register_in_rancher ? 1 : 0
  cluster_id = rancher2_cluster.this[0].id
}

#Waiting for the cluster to be synced with Rancher.
resource "time_sleep" "wait_300_seconds" {
  depends_on      = [rancher2_cluster_sync.this]
  create_duration = "300s"
}
