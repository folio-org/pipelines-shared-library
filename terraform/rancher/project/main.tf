resource "rancher2_project" "this" {
  provider                  = rancher2
  name                      = var.rancher_project_name
  cluster_id                = data.rancher2_cluster.this.id
  enable_project_monitoring = false
  container_resource_limit {
    limits_memory   = "256Mi"
    requests_memory = "128Mi"
  }
}

resource "rancher2_project_role_template_binding" "default" {
  count              = length(var.github_team_ids)
  name               = "github-team-${var.github_team_ids[count.index]}-binding"
  role_template_id   = "project-owner"
  project_id         = rancher2_project.this.id
  group_principal_id = "github_team://${var.github_team_ids[count.index]}"
}

resource "rancher2_project_role_template_binding" "read-only" {
  count              = data.rancher2_cluster.this.name == local.testing_cluster ? 1 : 0
  name               = "github-team-folio-org-binding"
  role_template_id   = "read-only"
  project_id         = rancher2_project.this.id
  group_principal_id = "github_org://${var.github_org_id}"
}

# Create a new rancher2 Namespace assigned to cluster project
resource "rancher2_namespace" "this" {
  name        = var.rancher_project_name
  project_id  = rancher2_project.this.id
  description = "${var.rancher_project_name} project namespace"
  container_resource_limit {
    limits_memory   = "256Mi"
    requests_memory = "128Mi"
  }
  labels = {
    team = var.rancher_project_name
  }
}

# Create a new rancher2 Project Registry
resource "rancher2_registry" "folio-docker" {
  name        = "folio-docker"
  description = "docker.dev.folio.org registry"
  project_id  = rancher2_project.this.id
  registries {
    address  = "docker.dev.folio.org"
    username = var.folio_docker_registry_username
    password = var.folio_docker_registry_password
  }
}
