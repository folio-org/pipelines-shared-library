resource "rancher2_secret" "kong-credentials" {
  data = {
    KONG_PG_USER     = base64encode("kong")
    KONG_PG_HOST     = base64encode(var.pg_embedded ? local.pg_service_writer : module.rds[0].cluster_endpoint)
    KONG_PG_PASSWORD = base64encode(local.pg_password)
    KONG_PG_PORT     = base64encode("5432")
    KONG_PG_DATABASE = base64encode("kong")
    KONG_PASSWORD    = base64encode("admin")
    KONG_ADMIN_USER  = base64encode("kong_admin")
    KONG_URL         = base64encode("http://kong-admin-api-${rancher2_namespace.this.id}")
  }
  project_id   = rancher2_project.this.id
  namespace_id = rancher2_namespace.this.id
  name         = "kong-credentials"
  count        = var.eureka ? 1 : 0
}
resource "helm_release" "kong" {
  count = var.eureka ? 1 : 0
  chart = "kong"
  depends_on = [
    rancher2_secret.db-credentials,
    helm_release.postgresql,
    helm_release.pgadmin,
    postgresql_database.kong,
    postgresql_role.kong,
    rancher2_secret.kong-credentials
  ]
  name       = "kong-${var.rancher_project_name}"
  namespace  = rancher2_namespace.this.id
  version    = "12.0.11"
  repository = "https://repository.folio.org/repository/helm-bitnami-proxy"
  values = [
    <<-EOF
image:
  registry: folioci
  repository: folio-kong
  tag: latest
  pullPolicy: Always
useDaemonset: false
replicaCount: 1
containerSecurityContext:
  enabled: true
  seLinuxOptions: {}
  runAsUser: 1001
  runAsGroup: 1001
  runAsNonRoot: true
  privileged: false
  readOnlyRootFilesystem: true
  allowPrivilegeEscalation: false
  capabilities:
    drop: ["ALL"]
  seccompProfile:
    type: "RuntimeDefault"
database: postgresql
postgresql:
  enabled: false
  external:
    host: ${var.pg_embedded ? local.pg_service_writer : module.rds[0].cluster_endpoint}
    port: 5432
    user: kong
    password: ""
    database: kong
    existingSecret: "kong-credentials"
    existingSecretPasswordKey: "KONG_PG_PASSWORD"
networkPolicy:
  enabled: false
service:
  type: NodePort
  exposeAdmin: true
  disableHttpPort: false
  ports:
    proxyHttp: 8000
    proxyHttps: 8443
    adminHttp: 8001
    adminHttps: 8002
  nodePorts:
    proxyHttp: ""
    proxyHttps: ""
    adminHttp: ""
    adminHttps: ""
    kongMgr: ""
ingress:
  ingressClassName: ""
  pathType: ImplementationSpecific
  path: /*
  hostname: ${join(".", [join("-", [data.rancher2_cluster.this.name, var.rancher_project_name, "kong"]), var.root_domain])}
  enabled: true
  annotations:
    kubernetes.io/ingress.class: "alb"
    alb.ingress.kubernetes.io/scheme: "internet-facing"
    alb.ingress.kubernetes.io/group.name: "${local.group_name}"
    alb.ingress.kubernetes.io/listen-ports: '[{"HTTPS":443}]'
    alb.ingress.kubernetes.io/success-codes: "200-399"
    alb.ingress.kubernetes.io/healthcheck-path: "/version"
  extraRules:
   - host: ${join(".", [join("-", ["ecs", data.rancher2_cluster.this.name, var.rancher_project_name, "kong"]), var.root_domain])}
     http:
       paths:
       - backend:
           service:
             name: kong-${var.rancher_project_name}
             port:
               name: http-proxy
         path: /*
         pathType: ImplementationSpecific
   - host: ${join(".", [join("-", [data.rancher2_cluster.this.name, var.rancher_project_name, "kong-ui"]), var.root_domain])}
     http:
       paths:
       - backend:
           service:
             name: kong-${var.rancher_project_name}
             port:
               name: https-admin
         path: /*
         pathType: ImplementationSpecific
kong:
  livenessProbe:
    enabled: false
  readinessProbe:
    enabled: false
  startupProbe:
    enabled: false
  extraEnvVars:
  - name: KONG_PASSWORD
    valueFrom:
       secretKeyRef:
         name: kong-credentials
         key: KONG_PASSWORD
  - name: KONG_ADMIN_GUI_PATH
    value: "/"
  - name: KONG_ADMIN_GUI_URL
    value: "localhost:8002"
  - name: KONG_ADMIN_GUI_API_URL
    value: "localhost:8001"
  - name: KONG_UPSTREAM_TIMEOUT
    value: "600000"
  - name: KONG_UPSTREAM_SEND_TIMEOUT
    value: "600000"
  - name: KONG_UPSTREAM_READ_TIMEOUT
    value: "600000"
  - name: KONG_NGINX_HTTP_CLIENT_MAX_BODY_SIZE
    value: "256m"
  - name: KONG_NGINX_PROXY_PROXY_NEXT_UPSTREAM
    value: "error timeout http_500 http_502 http_503 http_504"
  - name: "KONG_PROXY_SEND_TIMEOUT"
    value: "600000"
  - name: "KONG_UPSTREAM_CONNECT_TIMEOUT"
    value: "600000"
  - name: "KONG_PROXY_READ_TIMEOUT"
    value: "600000"
  - name: "KONG_NGINX_HTTP_KEEPALIVE_TIMEOUT"
    value: "600000"
  - name: "KONG_NGINX_UPSTREAM_KEEPALIVE"
    value: "600000"
  - name: "KONG_UPSTREAM_KEEPALIVE_IDLE_TIMEOUT"
    value: "600000"
  - name: "KONG_UPSTREAM_KEEPALIVE_POOL_SIZE"
    value: "1024"
  - name: "KONG_UPSTREAM_KEEPALIVE_MAX_REQUESTS"
    value: "20000"
  - name: "KONG_NGINX_HTTP_KEEPALIVE_REQUESTS"
    value: "20000"
  - name: KONG_PG_DATABASE
    value: "kong"
  - name: KONG_NGINX_PROXY_PROXY_BUFFERS
    value: "64 160k"
  - name: KONG_NGINX_PROXY_CLIENT_HEADER_BUFFER_SIZE
    value: "16k"
  - name: KONG_NGINX_HTTP_CLIENT_HEADER_BUFFER_SIZE
    value: "16k"
  - name: KONG_ADMIN_LISTEN
    value: "0.0.0.0:8001"
  - name: KONG_NGINX_PROXY_PROXY_BUFFER_SIZE
    value: "160k"
  - name: KONG_NGINX_PROXY_LARGE_CLIENT_HEADER_BUFFERS
    value: "4 16k"
  - name: KONG_PLUGINS
    value: "bundled"
  - name: KONG_MEM_CACHE_SIZE
    value: "2048m"
  - name: KONG_NGINX_HTTP_LARGE_CLIENT_HEADER_BUFFERS
    value: "4 16k"
  - name: KONG_LOG_LEVEL
    value: "info"
  - name: KONG_NGINX_HTTPS_LARGE_CLIENT_HEADER_BUFFERS
    value: "4 16k"
  - name: KONG_PROXY_LISTEN
    value: "0.0.0.0:8000"
  - name: KONG_NGINX_WORKER_PROCESSES
    value: "auto"
  - name: EUREKA_RESOLVE_SIDECAR_IP
    value: "false"
  resources:
    requests:
      ephemeral-storage: 50Mi
      memory: 2428Mi
    limits:
      ephemeral-storage: 1Gi
      memory: 3072Mi
ingressController:
  enabled: false
migration:
  command: ["/bin/sh", "-c"]
  args: ["echo 'Hello kong!'"]
EOF
  ]
}

resource "kubernetes_service" "kong_admin_api" {
  count = var.eureka ? 1 : 0
  metadata {
    name      = "kong-admin-api-${rancher2_namespace.this.id}"
    namespace = rancher2_namespace.this.id
  }
  spec {
    selector = {
      "app.kubernetes.io/component" = "server"
      "app.kubernetes.io/instance"  = "kong-${rancher2_namespace.this.id}"
      "app.kubernetes.io/name"      = "kong"
    }
    port {
      port        = 80
      target_port = 8001
    }
    type = "ClusterIP"
  }
}
