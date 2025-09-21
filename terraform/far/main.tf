resource "rancher2_project" "this" {
  name        = var.project_name
  cluster_id  = local.cluster_id
  description = "Project for ${var.project_name} in cluster ${var.cluster_name}"
  container_resource_limit {
    limits_memory   = "128Mi"
    requests_memory = "64Mi"
  }
}

resource "rancher2_namespace" "this" {
  name        = var.project_name
  project_id  = rancher2_project.this.id
  description = "${var.project_name} project namespace"
  labels = {
    team                                      = "Kitfox",
    "kubernetes.io/metadata.name"             = var.project_name,
    "elbv2.k8s.aws/pod-readiness-gate-inject" = "enabled"
  }
}

module "far_postgres_helm" {
  source = "./modules/far_postgres_db"

  chart_version        = var.postgres_chart_version
  cluster_name         = var.cluster_name
  cluster_id           = local.cluster_id
  namespace_name       = rancher2_namespace.this.name
  namespace_id         = rancher2_namespace.this.id
  tags                 = var.tags
  db_name              = "far_db"
  db_username          = "far_admin"
  ebs_size             = var.volume_size
  ebs_type             = var.volume_type
  snapshot_id          = var.snapshot_id
  existing_secret_name = var.existing_secret_name
  enable_backups       = var.enable_backups
}

module "far_mgr_app_helm" {
  source = "./modules/far_mgr_app"

  dependencies = [module.far_postgres_helm]

  chart_version                         = var.mgr_chart_version
  namespace_id                          = rancher2_namespace.this.id
  domain_name                           = var.domain_name
  db_secret_name                        = module.far_postgres_helm.db_secret_name
  image_repository                      = var.mgr_app_image_repository
  image_tag                             = var.mgr_app_image_tag
  memory_limit                          = var.mgr_app_memory_limit
  memory_request                        = var.mgr_app_memory_request
  autoscaling_enabled                   = var.mgr_app_autoscaling_enabled
  autoscaling_min_replicas              = var.mgr_app_autoscaling_min_replicas
  autoscaling_max_replicas              = var.mgr_app_autoscaling_max_replicas
  autoscaling_target_memory_utilization = var.mgr_app_autoscaling_target_memory_utilization
  extra_java_opts                       = var.mgr_app_extra_java_opts
}
