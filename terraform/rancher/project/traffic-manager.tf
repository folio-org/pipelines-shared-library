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
  namespaced: false
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
    resources  = ["pods"]
    verbs      = ["get", "list", "create", "delete", "patch", "update"]
  }
  rules {
    api_groups = [""]
    resources  = ["pods/portforward"]
    verbs      = ["*"]
  }
  rules {
    api_groups = [""]
    resources  = ["services"]
    verbs      = ["get", "list"]
  }
  rules {
    api_groups = [""]
    resources  = ["endpoints"]
    verbs      = ["get", "list"]
  }
  rules {
    api_groups = [""]
    resources  = ["networkpolicies"]
    verbs      = ["create", "get", "delete", "list", "patch", "update"]
  }
  rules {
    api_groups = [""]
    resources  = ["namespaces"]
    verbs      = ["get", "list"]
  }
  rules {
    api_groups = [""]
    resources  = ["serviceaccounts"]
    verbs      = ["get", "list", "create", "update"]
  }
  rules {
    api_groups = [""]
    resources  = ["roles", "rolebindings"]
    verbs      = ["get", "list"]
  }
}

resource "rancher2_project_role_template_binding" "access_port_forward" {
  count              = length(var.github_team_ids)
  name               = "port-forward-${var.github_team_ids[count.index]}-binding"
  role_template_id   = rancher2_role_template.port_forward.id
  project_id         = rancher2_project.this.id
  group_principal_id = "github_team://${var.github_team_ids[count.index]}"
}
