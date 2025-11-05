resource "helm_release" "traffic-manager" {
  count     = 1
  namespace = rancher2_namespace.this.name
  name      = "traffic-manager-${rancher2_namespace.this.name}"
  chart     = "oci://ghcr.io/telepresenceio/telepresence-oss"
  version   = "2.21.1"
  values = [
    <<-EOF
image:
  registry: 732722833398.dkr.ecr.us-west-2.amazonaws.com
  repository: tel2
  tag: 2.21.1
  pullPolicy: IfNotPresent
resources:
  limits:
    cpu: 256m
    memory: 512Mi
  requests:
    cpu: 128m
    memory: 128Mi
securityContext:
  capabilities:
    add: ["NET_ADMIN"]
agent:
  resources:
    requests:
      cpu: 128m
      memory: 256Mi
    limits:
      cpu: 256m
      memory: 512Mi
hooks:
  curl:
    registry: 732722833398.dkr.ecr.us-west-2.amazonaws.com
    image: "curl"
    tag: 7.88.1
    imagePullSecrets: []
    pullPolicy: IfNotPresent
managerRbac:
  create: true
  namespaced: true
  namespaces:
  - ${rancher2_namespace.this.name}
    EOF
  ]
}

resource "kubernetes_role" "port_forward_role" {
  metadata {
    name      = "port-forward-role"
    namespace = rancher2_namespace.this.id
  }
  rule {
    api_groups = ["apps"]
    resources  = ["deployments", "replicasets", "statefulsets"]
    verbs      = ["get", "list", "watch"]
  }
  rule {
    api_groups = [""]
    resources  = ["pods", "pods/log", "services"]
    verbs      = ["get", "list", "watch", "update"]
  }
  rule {
    api_groups = [""]
    resources  = ["pods/portforward"]
    verbs      = ["create"]
  }
  rule {
    api_groups = [""]
    resources  = ["configmaps"]
    verbs      = ["update", "get"]
  }
}

resource "kubernetes_role_binding" "port_forward_binding" {
  metadata {
    name      = "port-forward-binding"
    namespace = rancher2_namespace.this.id
  }
  subject {
    kind      = "User"
    name      = "rancher-port-forward"
    api_group = "rbac.authorization.k8s.io"
  }
  role_ref {
    kind      = "Role"
    name      = "port-forward-role"
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
