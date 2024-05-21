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
  enabled: true
  annotations: {}
  hosts:
    - kong.local
  tls: []

resources: {}

deployment:
  enabled: false
daemonSet:
  enabled: true
EOF
  ]
}
