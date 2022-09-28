resource "rancher2_project" "this" {
  provider                  = rancher2
  name                      = var.rancher_project_name
  cluster_id                = data.rancher2_cluster.this.id
  enable_project_monitoring = false
  container_resource_limit {
    limits_memory   = "512Mi"
    requests_memory = "400Mi"
  }
}

# Create a new rancher2 Project Role Template Binding
resource "rancher2_project_role_template_binding" "this" {
  for_each           = toset(var.github_team_ids)
  name               = var.rancher_project_name
  project_id         = rancher2_project.this.id
  role_template_id   = "project-member"
  group_principal_id = each.key
}

# Create a new rancher2 Namespace assigned to cluster project
resource "rancher2_namespace" "this" {
  name        = var.rancher_project_name
  project_id  = rancher2_project.this.id
  description = "${var.rancher_project_name} project namespace"
  container_resource_limit {
    limits_memory   = "512Mi"
    requests_memory = "400Mi"
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
