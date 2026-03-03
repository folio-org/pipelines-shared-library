resource "kubernetes_role" "debugger_deployment_access" {
  count = var.rancher_cluster_name == "folio-edev" ? 1 : 0

  metadata {
    name      = "debugger-deployment-access"
    namespace = rancher2_namespace.this.id
  }

  rule {
    api_groups = ["apps"]
    resources  = ["deployments"]
    verbs      = ["get", "list", "watch", "patch", "update"]
  }

  rule {
    api_groups = [""]
    resources  = ["pods"]
    verbs      = ["get", "list", "watch"]
  }

  rule {
    api_groups = [""]
    resources  = ["pods/log"]
    verbs      = ["get", "list", "watch"]
  }

  rule {
    api_groups = [""]
    resources  = ["pods/portforward"]
    verbs      = ["create", "get", "list"]
  }
}

resource "kubernetes_role_binding" "debugger_deployment_access" {
  count = var.rancher_cluster_name == "folio-edev" ? 1 : 0

  metadata {
    name      = "debugger-deployment-access"
    namespace = rancher2_namespace.this.id
  }

  subject {
    kind      = "Group"
    name      = "rancher-port-forward"
    api_group = "rbac.authorization.k8s.io"
  }

  role_ref {
    kind      = "Role"
    name      = "debugger-deployment-access"
    api_group = "rbac.authorization.k8s.io"
  }
}
