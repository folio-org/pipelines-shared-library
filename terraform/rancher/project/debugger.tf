resource "kubernetes_role" "debugger_deployment_access" {
  #count = var.rancher_cluster_name == "folio-edev" ? 1 : 0

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
  #count = var.rancher_cluster_name == "folio-edev" ? 1 : 0

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

data "aws_secretsmanager_secret" "telepresence_key" {
  name = "telepresence"
}

data "aws_secretsmanager_secret_version" "current" {
  secret_id = data.aws_secretsmanager_secret.telepresence_key.id
}

resource "kubernetes_config_map" "telepresence-cm" {
  metadata {
    namespace = rancher2_namespace.this.id
    name      = "telepresence-${rancher2_namespace.this.name}"
  }
  data = {
    AWS_KEY_ID     = tostring(jsondecode(data.aws_secretsmanager_secret_version.current.secret_string)["KEY"])
    AWS_SECRET_KEY = tostring(jsondecode(data.aws_secretsmanager_secret_version.current.secret_string)["SECRET"])
  }
}
