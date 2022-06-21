resource "rancher2_secret" "s3-postgres-backups-credentials" {
  name         = "s3-postgres-backups-credentials"
  project_id   = data.terraform_remote_state.project_state.outputs.rancher2_project_id
  namespace_id = data.terraform_remote_state.project_state.outputs.rancher2_project_namespace
  data = {
    POSTGRES_USER                = base64encode(data.terraform_remote_state.project_state.outputs.pg_username)
    POSTGRES_PASSWORD            = base64encode(data.terraform_remote_state.project_state.outputs.pg_password)
    POSTGRES_DATABASE            = base64encode(data.terraform_remote_state.project_state.outputs.pg_dbname)
    POSTGRES_HOST                = base64encode("pg-folio")
    S3_BACKUP_PATH               = base64encode("s3://folio-postgresql-backups")
    RANCHER_CLUSTER_PROJECT_NAME = base64encode(join("-", [var.rancher_cluster_name, var.rancher_project_name]))
    AWS_BUCKET                   = base64encode(var.s3_postgres_backups_bucket_name)
    AWS_ACCESS_KEY_ID            = base64encode(var.s3_postgres_backups_access_key)
    AWS_SECRET_ACCESS_KEY        = base64encode(var.s3_postgres_backups_secret_key)
  }
}
