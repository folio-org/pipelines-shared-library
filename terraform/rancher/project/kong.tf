resource "helm_release" "kong" {
  count      = var.eureka ? 1 : 0
  chart      = "kong"
  depends_on = [rancher2_secret.db-credentials]
  name       = "kong-${var.rancher_project_name}"
  namespace  = rancher2_namespace.this.id
  version    = "12.0.11"
  repository = "https://repository.folio.org/repository/helm-bitnami-proxy"
  values = [
    <<-EOF

replicaCount: 1

image:
  repository: kong
  tag: 2.3
  pullPolicy: IfNotPresent

env:
  database: "postgres"
  pg_host:
    valueFrom:
      secretKeyRef:
        name: db-connect-modules
        key: DB_HOST
  pg_user:
    valueFrom:
      secretKeyRef:
        name: db-connect-modules
        key: DB_KONG_USERNAME
  pg_password:
    valueFrom:
      secretKeyRef:
        name: db-connect-modules
        key: DB_PASSWORD
  pg_database:
    valueFrom:
      secretKeyRef:
        name: db-connect-modules
        key: postgresql-database

postgresql:
  enabled: false

migrations:
  enabled: true

service:
  type: LoadBalancer
  port: 80

ingress:
  hosts:
    - host: ${join(".", [join("-", [data.rancher2_cluster.this.name, var.rancher_project_name, "kong"]), var.root_domain])}
      paths:
        - path: /*
          pathType: ImplementationSpecific
  enabled: true
  annotations:
    kubernetes.io/ingress.class: alb
    alb.ingress.kubernetes.io/scheme: internet-facing
    alb.ingress.kubernetes.io/group.name: ${local.kong_url}
    alb.ingress.kubernetes.io/listen-ports: '[{"HTTPS":443}]'
    alb.ingress.kubernetes.io/success-codes: 200-399
    alb.ingress.kubernetes.io/healthcheck-path: /misc/ping
    alb.ingress.kubernetes.io/healthcheck-port: '80'

resources:
  requests:
    memory: 1Gi
  limits:
    memory: 2Gi

deployment:
  enabled: false
daemonSet:
  enabled: true
EOF
  ]
}
