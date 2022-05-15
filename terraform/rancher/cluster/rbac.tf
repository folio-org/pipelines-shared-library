## Create developers Role using RBAC
#resource "kubernetes_cluster_role" "iam_roles_developers" {
#  metadata {
#    name = join("-", [var.iac_environment_tag, var.rancher_cluster_name, "developers"])
#  }
#  rule {
#    api_groups = ["*"]
#    resources  = ["pods", "pods/log", "deployments", "ingresses", "services"]
#    verbs      = ["get", "list"]
#  }
#  rule {
#    api_groups = ["*"]
#    resources  = ["pods/exec"]
#    verbs      = ["create"]
#  }
#  rule {
#    api_groups = ["*"]
#    resources  = ["pods/portforward"]
#    verbs      = ["*"]
#  }
#}
#
## Bind developer Users with their Role
#resource "kubernetes_cluster_role_binding" "iam_roles_developers" {
#  metadata {
#    name = join("-", [var.iac_environment_tag, var.rancher_cluster_name, "developers"])
#  }
#
#  role_ref {
#    api_group = "rbac.authorization.k8s.io"
#    kind      = "ClusterRole"
#    name      = join("-", [var.iac_environment_tag, var.rancher_cluster_name, "developers"])
#  }
#
#  dynamic "subject" {
#    for_each = toset(var.developer_users)
#
#    content {
#      name      = subject.key
#      kind      = "User"
#      api_group = "rbac.authorization.k8s.io"
#    }
#  }
#}
#
## Helm RBAC
#resource "kubernetes_service_account" "helm" {
#  metadata {
#    name      = "helm"
#    namespace = "kube-system"
#  }
#  automount_service_account_token = true
#}
#
#resource "kubernetes_cluster_role_binding" "helm" {
#  metadata {
#    name = "helm"
#  }
#  role_ref {
#    kind      = "ClusterRole"
#    name      = "cluster-admin"
#    api_group = "rbac.authorization.k8s.io"
#  }
#  subject {
#    kind      = "ServiceAccount"
#    name      = "default"
#    namespace = "kube-system"
#  }
#  subject {
#    kind      = "ServiceAccount"
#    name      = kubernetes_service_account.helm.metadata[0].name
#    namespace = "kube-system"
#  }
#}
