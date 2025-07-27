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

  chart_version  = var.postgres_chart_version
  cluster_name   = var.cluster_name
  cluster_id     = local.cluster_id
  namespace_name = rancher2_namespace.this.name
  namespace_id   = rancher2_namespace.this.id
  tags           = var.tags
  db_name        = "far_db"
  db_username    = "far_admin"
  ebs_size       = var.volume_size
  ebs_type       = var.volume_type
  snapshot_id    = var.snapshot_id
}

module "far_mgr_app_helm" {
  source = "./modules/far_mgr_app"

  dependencies = [module.far_postgres_helm]

  chart_version  = var.mgr_chart_version
  namespace_id   = rancher2_namespace.this.id
  domain_name    = var.domain_name
  db_secret_name = module.far_postgres_helm.db_secret_name
}
