resource "rancher2_project" "traffic-manager" {
  count       = var.enable_telepresence ? 1 : 0
  depends_on  = [module.eks_cluster.eks_managed_node_groups]
  name        = "traffic-manager"
  description = "Project for Traffic Manager in cluster"
  cluster_id  = rancher2_cluster_sync.this[0].cluster_id
  provider    = rancher2
}

resource "rancher2_namespace" "traffic-manager" {
  count       = var.enable_telepresence ? 1 : 0
  depends_on  = [module.eks_cluster.eks_managed_node_groups]
  name        = "traffic-manager"
  project_id  = rancher2_project.traffic-manager[0].id
  description = "Namespace for Traffic Manager in cluster"
  provider    = rancher2
}


resource "helm_release" "traffic-manager" {
  count      = var.enable_telepresence ? 1 : 0
  depends_on = [rancher2_namespace.traffic-manager]
  namespace  = rancher2_namespace.traffic-manager[0].name
  name       = "traffic-manager"
  chart      = "oci://ghcr.io/telepresenceio/telepresence-oss"
  version    = "2.25.0"
  values = [
    <<-EOF
image:
  registry: 732722833398.dkr.ecr.us-west-2.amazonaws.com
  tag: 2.25.0
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
  namespaceSelector:
    matchExpressions:
      - key: kubernetes.io/metadata.name
        operator: NotIn
        values:
          - kube-system
          - kube-node-lease
          - cattle-system
          - cattle-fleet-system
          - cattle-impersonation-system
          - default
          - kube-node-lease
          - kube-public
          - kube-system
          - kubecost
          - local
          - logging
          - monitoring
          - sorry-cypress
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
  count      = var.enable_telepresence ? 1 : 0
  depends_on = [helm_release.traffic-manager]

  provisioner "local-exec" {
    command = <<-EOF
      aws eks update-kubeconfig --name ${rancher2_cluster_sync.this[0].cluster_id} --region ${var.aws_region}

      sleep 10

      kubectl patch deployment traffic-manager \
        -n ${rancher2_namespace.traffic-manager[0].name} \
        -p '{"spec":{"template":{"spec":{"dnsPolicy":"ClusterFirstWithHostNet","hostNetwork":true}}}}'
    EOF
  }

  triggers = {
    helm_release_version = helm_release.traffic-manager[0].version
    namespace            = rancher2_namespace.traffic-manager[0].name
    cluster_name         = rancher2_cluster_sync.this[0].cluster_id
  }
}
