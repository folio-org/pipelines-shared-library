resource "helm_release" "traffic-manager" {
  count     = 1
  namespace = rancher2_namespace.this.name
  name      = "traffic-manager"
  chart     = "oci://ghcr.io/telepresenceio/telepresence-oss"
  version   = "2.23.6"
  values = [
    <<-EOF
image:
  registry: 732722833398.dkr.ecr.us-west-2.amazonaws.com
  tag: 2.23.6
  pullPolicy: IfNotPresent
resources:
  limits:
    cpu: 256m
    memory: 1024Mi
  requests:
    cpu: 128m
    memory: 128Mi
agentInjector:
  enabled: true
  name: agent-injector
  secret:
    name: mutator-webhook-tls
  certificate:
    accessMethod: watch
    method: helm
    certmanager:
      commonName: agent-injector
      duration: 2160h0m0s
      issuerRef:
        name: telepresence
        kind: Issuer
  injectPolicy: OnDemand
  webhook:
    name: agent-injector-webhook
    admissionReviewVersions: ["v1"]
    servicePath: /traffic-agent
    port: 443
    failurePolicy: Ignore
    reinvocationPolicy: IfNeeded
    sideEffects: None
    timeoutSeconds: 5
agent:
  resources:
    requests:
      cpu: 128m
      memory: 256Mi
    limits:
      cpu: 256m
      memory: 512Mi
  appProtocolStrategy: http2Probe
  port: 9900
  mountPolicies:
    "/tmp": Local
  image:
    pullPolicy: IfNotPresent
  initContainer:
    enabled: true      
hooks:
  curl:
    registry: 732722833398.dkr.ecr.us-west-2.amazonaws.com
    image: "curl"
    tag: 8.1.1
    imagePullSecrets: []
    pullPolicy: IfNotPresent
managerRbac:
  create: true  
    EOF
  ]
}

resource "null_resource" "patch_traffic_manager_host_network" {
  depends_on = [helm_release.traffic-manager]

  provisioner "local-exec" {
    command = <<-EOF
      aws eks update-kubeconfig --name ${data.rancher2_cluster.this.name} --region ${var.aws_region}

      sleep 10

      kubectl patch deployment traffic-manager \
        -n ${rancher2_namespace.this.name} \
        -p '{"spec":{"template":{"spec":{"dnsPolicy":"ClusterFirstWithHostNet","hostNetwork":true}}}}'
    EOF
  }

  triggers = {
    helm_release_version = helm_release.traffic-manager[0].version
    namespace            = rancher2_namespace.this.name
    cluster_name         = data.rancher2_cluster.this.name
  }
}

resource "kubernetes_role" "port_forward_role" {
  metadata {
    name      = "port-forward-role"
    namespace = rancher2_namespace.this.id
  }
  rule {
    api_groups = ["apps"]
    resources  = ["deployments", "replicasets", "statefulsets"]
    verbs      = ["get", "list", "watch", "patch", "update"]
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
