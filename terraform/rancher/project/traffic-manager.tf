resource "helm_release" "traffic-manager" {
  count     = 1
  namespace = rancher2_namespace.this.name
  name      = "traffic-manager-${rancher2_namespace.this.name}"
  chart     = "oci://ghcr.io/telepresenceio/telepresence-oss"
  version   = "2.21.1"
  values = [
    <<-EOF
image:
  registry: ghcr.io/telepresenceio
  tag: 2.20.1
managerRbac:
  create: true
  namespaced: true
  namespaces:
  - ${rancher2_namespace.this.name}
    EOF
  ]
}

resource "rancher2_role_template" "port_forward" {
  name         = "port-forward-access"
  context      = "project"
  default_role = false
  description  = "Terraform role for telepresence"
  rules {
    api_groups = [""]
    resources  = ["namespaces", "services", "pods"]
    verbs      = ["get", "watch", "list", "view"]
  }
  rules {
    api_groups = [""]
    resources  = ["pods/log"]
    verbs      = ["get", "watch", "list", "view"]
  }
  rules {
    api_groups = [""]
    resources  = ["pods/portforward"]
    verbs      = ["create", "get", "watch", "list", "view"]
  }
  rules {
    api_groups = ["apps"]
    resources  = ["deployments", "replicasets", "statefulsets"]
    verbs      = ["get", "watch", "list", "view"]
  }
}

resource "rancher2_project_role_template_binding" "access_port_forward" {
  count              = length(var.github_team_ids)
  name               = "port-forward-${var.github_team_ids[count.index]}-binding"
  role_template_id   = rancher2_role_template.port_forward.id
  project_id         = rancher2_project.this.id
  group_principal_id = "github_team://${var.github_team_ids[count.index]}"
}
