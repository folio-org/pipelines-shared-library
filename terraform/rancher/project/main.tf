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
    team                                      = var.rancher_project_name,
    "kubernetes.io/metadata.name"             = var.rancher_project_name,
    "elbv2.k8s.aws/pod-readiness-gate-inject" = "enabled"
  }
}

resource "kubectl_manifest" "hazelcast-cluster-role" {
  provider           = kubectl
  override_namespace = rancher2_namespace.this.name
  yaml_body          = <<YAML
---
apiVersion: rbac.authorization.k8s.io/v1
kind: ClusterRole
metadata:
  name: hazelcast-cluster-role
rules:
  - apiGroups:
      - ""
      # Access to apps API is only required to support automatic cluster state management
      # when persistence (hot-restart) is enabled.
      - apps
    resources:
      - endpoints
      - pods
      - nodes
      - services
      # Access to statefulsets resource is only required to support automatic cluster state management
      # when persistence (hot-restart) is enabled.
      - statefulsets
    verbs:
      - get
      - list
      # Watching resources is only required to support automatic cluster state management
      # when persistence (hot-restart) is enabled.
      - watch
  - apiGroups:
      - "discovery.k8s.io"
    resources:
      - endpointslices
    verbs:
      - get
      - list
YAML
}

# Create a new rancher2 Project Registry
resource "rancher2_registry" "folio-docker" {
  name        = "folio-docker"
  description = "docker hub registry"
  project_id  = rancher2_project.this.id
  registries {
    address  = "index.docker.io"
    username = var.folio_docker_registry_username
    password = var.folio_docker_registry_password
  }
}

resource "kubernetes_secret" "docker_hub_credentials" {
  metadata {
    name      = "docker-hub-creds"
    namespace = rancher2_namespace.this.name
  }

  type = "kubernetes.io/dockerconfigjson"

  data = {
    ".dockerconfigjson" = base64encode(jsonencode({
      auths = {
        "https://index.docker.io/v1/" = {
          username = data.aws_ssm_parameter.docker_username.value
          password = data.aws_ssm_parameter.docker_password.value
          email    = "jenkins@indexdata.com"
          auth     = base64encode("${data.aws_ssm_parameter.docker_username.value}:${data.aws_ssm_parameter.docker_password.value}")
        }
      }
    }))
  }
}

resource "kubernetes_service_account" "default_patched" {
  depends_on = [
    kubernetes_secret.docker_hub_credentials,
    rancher2_namespace.this
  ]
  metadata {
    name      = "default"
    namespace = rancher2_namespace.this.name
  }
  image_pull_secret {
    name = kubernetes_secret.docker_hub_credentials.metadata[0].name
  }

  automount_service_account_token = true
  lifecycle {
    ignore_changes = [
      metadata
    ]
  }
}

