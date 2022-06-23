# Create a new rancher2 Project Registry
resource "rancher2_registry" "docker-folio-dev-org" {
  name        = "docker-folio-dev-org"
  description = "docker-folio.dev.folio.org registry"
  project_id  = data.rancher2_project.project.id
  registries {
    address  = "docker-folio.dev.folio.org"
    username = var.docker_folio_dev_registry_username
    password = var.docker_folio_dev_registry_password
  }
}

resource "helm_release" "psql-dump" {
  depends_on    = [rancher2_secret.s3-postgres-backups-credentials,rancher2_registry.docker-folio-dev-org]
  name          = "psql-dump"
  namespace     = var.rancher_project_name
  repository    = "https://repository.folio.org/repository/helm-hosted/"
  chart         = "psql-dump"
  devel         = "true"
  force_update  = "true"
  wait_for_jobs = "true"
  timeout       = "900"
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
