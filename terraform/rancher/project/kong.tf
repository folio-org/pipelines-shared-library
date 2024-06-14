resource "random_integer" "node_port" {
  max   = 32766
  min   = 30001
  count = var.eureka ? 4 : 0
}

resource "rancher2_secret" "kong-credentials" {
  data = {
    KONG_PG_USER     = base64encode("kong")
    KONG_PG_HOST     = base64encode(var.pg_embedded ? local.pg_service_writer : module.rds[0].cluster_endpoint)
    KONG_PG_PASSWORD = base64encode(local.pg_password)
    KONG_PG_PORT     = base64encode("5432")
    KONG_PG_DATABASE = base64encode(var.eureka ? local.pg_eureka_db_name : var.pg_dbname)
    KONG_PASSWORD    = base64encode("admin")
    KONG_ADMIN_USER  = base64encode("kong_admin")
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
    random_integer.node_port,
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
  registry: 732722833398.dkr.ecr.us-west-2.amazonaws.com
  repository: folio-kong
  tag: latest
  pullPolicy: IfNotPresent
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
    proxyHttp: 80
    proxyHttps: 443
    adminHttp: 8001
    adminHttps: 8444
  nodePorts:
    proxyHttp: "${tostring(random_integer.node_port[0].result - 1)}"
    proxyHttps: "${tostring(random_integer.node_port[1].result + 1)}"
    adminHttp: "${tostring(random_integer.node_port[2].result - 1)}"
    adminHttps: "${tostring(random_integer.node_port[3].result + 1)}"
ingress:
  ingressClassName: ""
  pathType: ImplementationSpecific
  path: /
  hostname: ${join(".", [join("-", [data.rancher2_cluster.this.name, var.rancher_project_name, "kong"]), var.root_domain])}
  enabled: true
  annotations:
    kubernetes.io/ingress.class: "alb"
    alb.ingress.kubernetes.io/scheme: "internet-facing"
    alb.ingress.kubernetes.io/group.name: "${local.group_name}"
    alb.ingress.kubernetes.io/listen-ports: '[{"HTTPS":443}]'
    alb.ingress.kubernetes.io/success-codes: "200-399"
    alb.ingress.kubernetes.io/healthcheck-path: "/"
    alb.ingress.kubernetes.io/healthcheck-port: "${tostring(random_integer.node_port[2].result)}"
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
   - name: KONG_ADMIN_GUI_API_URL
     value: "${local.kong_url}"
   - name: KONG_NGINX_HTTPS_LARGE_CLIENT_HEADER_BUFFERS
     value: "4 16k"
   - name: KONG_PROXY_LISTEN
     value: "0.0.0.0:8000"
   - name: KONG_NGINX_WORKER_PROCESSES
     value: "2"
   - name: EUREKA_RESOLVE_SIDECAR_IP
     value: "false"
ingressController:
  enabled: false
migration:
  command: ["/bin/sh", "-c"]
  args: ["echo 'Hello kong!'"]
EOF
  ]
}

resource "kubernetes_service" "kong_svc" {
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
