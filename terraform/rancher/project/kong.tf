resource "random_integer" "node_port" {
  max     = 32767
  min     = 30000

}

resource "helm_release" "kong" {
  count      = var.eureka ? 1 : 0
  chart      = "kong"
  depends_on = [rancher2_secret.db-credentials, random_integer.node_port, helm_release.postgresql, helm_release.pgadmin]
  name       = "kong-${var.rancher_project_name}"
  namespace  = rancher2_namespace.this.id
  version    = "12.0.11"
  repository = "https://repository.folio.org/repository/helm-bitnami-proxy"
  values = [<<-EOF
replicaCount: 1
image:
  registry: 732722833398.dkr.ecr.us-west-2.amazonaws.com
  repository: kong
  tag: testing
  pullPolicy: IfNotPresent
ingressController:
  enabled: true
admin:
  enabled: true
  type: NodePort
  http:
    enabled: true
    servicePort: 8001
    nodePort: ${random_integer.node_port.result}
  tls:
    enabled: false
proxy:
  enabled: true
  type: LoadBalancer
  http:
    enabled: true
    servicePort: 8000
  tls:
    enabled: true
    servicePortTls: 8443
  annotations: {}
ingress:
  hosts:
    - host: ${local.kong_url}
      paths:
        - path: /*
          pathType: ImplementationSpecific
          backend:
              serviceName: kong-admin
              servicePort: 8001
  enabled: true
  annotations:
    kubernetes.io/ingress.class: alb
    alb.ingress.kubernetes.io/scheme: internet-facing
    alb.ingress.kubernetes.io/group.name: ${local.group_name}
    alb.ingress.kubernetes.io/listen-ports: '[{"HTTPS":443}]'
    alb.ingress.kubernetes.io/success-codes: 200-399
    alb.ingress.kubernetes.io/healthcheck-path: /
    alb.ingress.kubernetes.io/healthcheck-port: '8000'
  tls: []
env:
  database: "postgres"
  pg_host:
    valueFrom:
      secretKeyRef:
        name: db-credentials
        key: DB_HOST
  pg_user:
    valueFrom:
      secretKeyRef:
        name: db-credentials
        key: DB_KONG_USERNAME
  pg_password:
    valueFrom:
      secretKeyRef:
        name: db-credentials
        key: DB_PASSWORD
  pg_database:
    valueFrom:
      secretKeyRef:
        name: db-credentials
        key: postgresql-database
  pg_port: 5432
  prefix: "/usr/local/kong"
migrations:
  enabled: true
rbac:
  create: true
  namespaced: true
serviceMonitor:
  enabled: false
telemetry:
  enabled: false
resources:
  limits:
    cpu: 1000m
    memory: 1Gi
  requests:
    cpu: 500m
    memory: 512Mi
nodeSelector: {}
tolerations: []
affinity: {}
podAnnotations: {}
podSecurityContext:
  fsGroup: 1000
securityContext:
  runAsUser: 1000
  runAsGroup: 1000
  capabilities:
    drop:
      - ALL
serviceAccount:
  create: true
  name: kong
logLevel: "debug"
postgresql:
  enabled: false
services:
  kong-admin:
    type: NodePort
    ports:
      - name: admin
        port: 8001
        targetPort: 8000
        nodePort: ${random_integer.node_port.result}
    selector:
      app: kong
    labels:
      app: kong
podLabels:
  app: kong
EOF
  ]
}
