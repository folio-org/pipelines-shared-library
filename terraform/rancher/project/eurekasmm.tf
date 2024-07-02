resource "aws_ssm_parameter" "folio-backend-admin-client" {
  count = var.eureka ? 1 : 0
  name  = join("_", [var.rancher_cluster_name, rancher2_namespace.this.name, "master_folio-backend-admin-client"])
  type  = "SecureString"
  value = "SecretPassword"
}

resource "aws_ssm_parameter" "mgr-applications" {
  count = var.eureka ? 1 : 0
  name  = join("_", [var.rancher_cluster_name, rancher2_namespace.this.name, "master_mgr-applications"])
  type  = "SecureString"
  value = "SecretPassword"
}

resource "aws_ssm_parameter" "mgr-tenant-entitlements" {
  count = var.eureka ? 1 : 0
  name  = join("_", [var.rancher_cluster_name, rancher2_namespace.this.name, "master_mgr-tenant-entitlements"])
  type  = "SecureString"
  value = "SecretPassword"
}

resource "aws_ssm_parameter" "mgr-tenants" {
  count = var.eureka ? 1 : 0
  name  = join("_", [var.rancher_cluster_name, rancher2_namespace.this.name, "master_mgr-tenants"])
  type  = "SecureString"
  value = "SecretPassword"
}
