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

resource "kubernetes_role_v1" "port_forward" {
  metadata {
    name      = "port_forward"
    namespace = rancher2_project.this.id
    labels = {
      name = "port_forward"
    }
  }
  rule {
    api_groups = [""]
    resources  = ["pods"]
    verbs      = ["get", "list", "watch"]
  }
  rule {
    api_groups = [""]
    resources  = ["pods/portforward"]
    verbs      = ["create"]
  }
  rule {
    api_groups = [""]
    resources  = ["services"]
    verbs      = ["get", "list"]
  }
  rule {
    api_groups = [""]
    resources  = ["endpoints"]
    verbs      = ["get", "list"]
  }
  rule {
    api_groups = [""]
    resources  = ["networkpolicies"]
    verbs      = ["get", "list"]
  }
  rule {
    api_groups = [""]
    resources  = ["namespaces"]
    verbs      = ["get", "list"]
  }
  rule {
    api_groups = [""]
    resources  = ["serviceaccounts"]
    verbs      = ["get", "list"]
  }
  rule {
    api_groups = [""]
    resources  = ["roles", "rolebindings"]
    verbs      = ["get", "list"]
  }
}

resource "kubernetes_role_binding_v1" "port_forward_access" {
  metadata {
    name      = "port_forward_access"
    namespace = rancher2_project.this.id
  }
  role_ref {
    api_group = "rbac.authorization.k8s.io"
    kind      = "Role"
    name      = kubernetes_role_v1.port_forward.id
  }
  subject {
    kind      = "User"
    name      = "rancher-port-forward"
    api_group = "rbac.authorization.k8s.io"
  }
}
