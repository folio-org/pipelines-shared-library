resource "rancher2_secret" "s3-postgres-backups-credentials" {
  name         = "s3-postgres-backups-credentials"
  project_id   = data.rancher2_project.project.id
  namespace_id = var.rancher_project_name
  data = {
    S3_BACKUP_PATH               = base64encode(var.s3_backup_path)
    RANCHER_CLUSTER_PROJECT_NAME = base64encode(join("-", [var.rancher_cluster_name, var.rancher_project_name]))
    AWS_ACCESS_KEY_ID            = base64encode(var.s3_postgres_backups_access_key)
    AWS_SECRET_ACCESS_KEY        = base64encode(var.s3_postgres_backups_secret_key)
  }
}
