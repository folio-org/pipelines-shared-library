resource "helm_release" "vault" {
  count = var.eureka ? 1 : 0
  chart = "vault"
  name  = "vault-${var.rancher_project_name}"
  namespace = rancher2_namespace.this.id
  repository = "https://helm.releases.hashicorp.com"
  version = "0.28.0"
  values = [<<-EOF
global:
  enabled: true
server:
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
      memory: "1Gi"
      cpu: "1024m"
    limits:
      memory: "256Mi"
      cpu: "200m"
  backup:
    enabled: false
  logLevel: "debug"
  ingress:
    enabled: false
    annotations: {}
    hosts:
      - host: vault.local
        paths:
          - path: /
            pathType: ImplementationSpecific
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
