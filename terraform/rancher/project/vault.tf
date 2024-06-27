resource "helm_release" "vault" {
  count      = 0
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
    enabled: false
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

resource "kubernetes_service" "vault-svc" {
  count = 0
  metadata {
    name      = "vault-${var.rancher_project_name}-np"
    namespace = rancher2_namespace.this.id
  }
  spec {
    selector = {
      "app.kubernetes.io/instance" : "vault-${var.rancher_project_name}"
      "app.kubernetes.io/name" : "vault"
    }
    session_affinity = "ClientIP"
    port {
      port        = 80
      target_port = 8200
    }
    type = "NodePort"
  }
}


resource "kubernetes_ingress_v1" "vault-ingress-ui" {
  count = 0
  metadata {
    name      = "vault-${var.rancher_project_name}-ui"
    namespace = var.rancher_project_name
    annotations = {
      "kubernetes.io/ingress.class" : "alb"
      "alb.ingress.kubernetes.io/scheme" : "internet-facing"
      "alb.ingress.kubernetes.io/group.name" : local.group_name
      "alb.ingress.kubernetes.io/listen-ports" : "[{\"HTTPS\":443}]"
      "alb.ingress.kubernetes.io/success-codes" : "200-399"
      "alb.ingress.kubernetes.io/healthcheck-path" : "/"
      "alb.ingress.kubernetes.io/healthcheck-port" : "8200"
    }
  }
  spec {
    ingress_class_name = ""
    rule {
      host = join(".", [join("-", [data.rancher2_cluster.this.name, var.rancher_project_name, "vault"]), var.root_domain])
      http {
        path {
          path = "/*"
          backend {
            service {
              name = "vault-${var.rancher_project_name}-np"
              port {
                number = 80
              }
            }
          }
        }
      }
    }
  }
}
