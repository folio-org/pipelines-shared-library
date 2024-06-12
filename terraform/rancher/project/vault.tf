resource "helm_release" "vault" {
  count      = var.eureka ? 1 : 0
  chart      = "vault"
  name       = "vault-${var.rancher_project_name}"
  namespace  = rancher2_namespace.this.id
  repository = "https://helm.releases.hashicorp.com"
  version    = "0.28.0"
  values = [<<-EOF
global:
  enabled: true
server:
  ingress:
    enabled: true
    annotations:
      kubernetes.io/ingress.class: "alb"
      alb.ingress.kubernetes.io/scheme: "internet-facing"
      alb.ingress.kubernetes.io/group.name: "${local.group_name}"
      alb.ingress.kubernetes.io/listen-ports: '[{"HTTPS":443}]'
      alb.ingress.kubernetes.io/success-codes: "200-399"
      alb.ingress.kubernetes.io/healthcheck-path: "/"
      alb.ingress.kubernetes.io/healthcheck-port: "8200"
    ingressClassName: ""
    pathType: ImplementationSpecific
    activeService: false
    hosts:
      - host: ${join(".", [join("-", [data.rancher2_cluster.this.name, var.rancher_project_name, "vault"]), var.root_domain])}
        paths:
         - path: /
           backend:
             service:
               name: vault-${var.rancher_project_name}-ui
               port:
                 number: 8200
    tls: []
  dev:
    enabled: true
  ha:
    enabled: false
  service:
    type: ClusterIP
    port: 8200
  dataStorage:
    enabled: true
  tls:
    enabled: false
    auto:
      enabled: false
  extraEnvironmentVars:
    VAULT_DEV_ROOT_TOKEN_ID: "root"
  resources:
    requests:
      memory: "256Mi"
      cpu: "100m"
    limits:
      memory: "1Gi"
      cpu: "1024m"
  backup:
    enabled: false
  logLevel: "debug"
  dataStorage:
    enabled: false
  auditLog:
    enabled: false
  agentInjector:
    enabled: false
  metrics:
    enabled: false
  unsealConfig:
    enabled: false
ui:
  enabled: true
  EOF
  ]
}
