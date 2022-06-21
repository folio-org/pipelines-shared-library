resource "helm_release" "db-backuper" {
  #depends_on    = [rancher2_secret.s3-postgres-backups-credentials]
  name          = "psql-dump"
  namespace     = var.rancher_project_name
  repository    = "https://repository.folio.org/repository/helm-hosted/"
  chart         = "psql-dump"
  devel         = "true"
  force_update  = "true"
  wait_for_jobs = "true"
  set {
    name  = "psql.projectNamespace"
    value = var.rancher_project_name
  }
  set {
    name  = "psql.job.image.repository"
    value = "bblayhub/pg-dump-restore-aws-cli"
  }
  set {
    name  = "psql.job.image.tag"
    value = "1.8"
  }
}
