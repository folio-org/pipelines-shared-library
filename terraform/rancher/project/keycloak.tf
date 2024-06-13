resource "rancher2_secret" "keycloak-credentials" {
  data = {
    KC_DB_URL_HOST                  = base64encode(var.pg_embedded ? local.pg_service_writer : module.rds[0].cluster_endpoint)
    KC_DB_URL_PORT                  = base64encode("5432")
    KC_DB_URL_DATABASE              = base64encode("keycloak")
    KC_DB_USERNAME                  = base64encode("keycloak")
    KC_DB_PASSWORD                  = base64encode(local.pg_password)
    KC_FOLIO_BE_ADMIN_CLIENT_SECRET = base64encode("SecretPassword")
    KC_HTTPS_KEY_STORE_PASSWORD     = base64encode("SecretPassword")
    KEYCLOAK_ADMIN_USER             = base64encode("admin")
    KEYCLOAK_ADMIN_PASSWORD         = base64encode("SecretPassword")
  }
  project_id   = rancher2_project.this.id
  namespace_id = rancher2_namespace.this.id
  name         = "keycloak-credentials"
  count        = var.eureka ? 1 : 0
}

data "rancher2_secret" "keycloak_credentials" {
  count        = (var.eureka ? 1 : 0)
  name         = "keycloak-credentials"
  project_id   = rancher2_project.this.id
  namespace_id = rancher2_namespace.this.id
  depends_on   = [helm_release.pgadmin, rancher2_secret.keycloak-credentials]
}

locals {
  kc_admin_user_name  = (var.eureka ? base64decode(lookup(data.rancher2_secret.keycloak_credentials[0].data, "KEYCLOAK_ADMIN_USER", "admin")) : "")
  kc_target_http_port = "8080"
}

resource "helm_release" "keycloak" {
  count      = (var.eureka ? 1 : 0)
  chart      = "keycloak"
  depends_on = [rancher2_secret.keycloak-credentials, helm_release.postgresql]
  name       = "keycloak-${var.rancher_project_name}"
  namespace  = rancher2_namespace.this.id
  version    = "21.0.4"
  repository = "https://repository.folio.org/repository/helm-bitnami-proxy"
  values = [
    <<-EOF
image:
  registry: 732722833398.dkr.ecr.us-west-2.amazonaws.com
  repository: folio-keycloak
  tag: latest
  pullPolicy: Always
  debug: false

auth:
  adminUser: ${local.kc_admin_user_name}
  existingSecret: keycloak-credentials
  passwordSecretKey: KEYCLOAK_ADMIN_PASSWORD

extraEnvVars:
  - name: FIPS
    value: "false"
  - name: EUREKA_RESOLVE_SIDECAR_IP
    value: "false"
  - name: PROXY_ADDRESS_FORWARDING
    value: "true"
  - name: KC_FOLIO_BE_ADMIN_CLIENT_SECRET
    valueFrom:
      secretKeyRef:
        name: keycloak-credentials
        key: KC_FOLIO_BE_ADMIN_CLIENT_SECRET
  - name: KC_HTTPS_KEY_STORE_PASSWORD
    valueFrom:
      secretKeyRef:
        name: keycloak-credentials
        key: KC_HTTPS_KEY_STORE_PASSWORD
  - name: KC_HOSTNAME
    value: ${local.keycloak_url}
  - name: KC_HOSTNAME_STRICT
    value: "false"
  - name: KC_HOSTNAME_STRICT_HTTPS
    value: "false"
  - name: KC_HOSTNAME_DEBUG
    value: "true"
  - name: KC_DB_PASSWORD
    valueFrom:
      secretKeyRef:
        name: keycloak-credentials
        key: KC_DB_PASSWORD
  - name: KC_DB_URL_DATABASE
    valueFrom:
      secretKeyRef:
        name: keycloak-credentials
        key: KC_DB_URL_DATABASE
  - name: KC_DB_URL_HOST
    valueFrom:
      secretKeyRef:
        name: keycloak-credentials
        key: KC_DB_URL_HOST
  - name: KC_DB_URL_PORT
    valueFrom:
      secretKeyRef:
        name: keycloak-credentials
        key: KC_DB_URL_PORT
  - name: KC_DB_USERNAME
    valueFrom:
      secretKeyRef:
        name: keycloak-credentials
        key: KC_DB_USERNAME
  - name: KC_PROXY
    value: edge
  - name: KC_HTTP_ENABLED
    value: "true"
  - name: KC_HTTP_PORT
    value: "${local.kc_target_http_port}"
  - name: KC_HEALTH_ENABLED
    value: "true"

postgresql:
  enabled: false

externalDatabase:
  existingSecret: keycloak-credentials
  existingSecretHostKey: KC_DB_URL_HOST
  existingSecretPortKey: KC_DB_URL_PORT
  existingSecretUserKey: KC_DB_USERNAME
  existingSecretDatabaseKey: KC_DB_URL_DATABASE
  existingSecretPasswordKey: KC_DB_PASSWORD

logging:
  output: default
  level: DEBUG

proxy: edge

enableDefaultInitContainers: false

containerSecurityContext:
  enabled: false

service:
  type: NodePort
  http:
    enabled: true
  ports:
    http: 80

networkPolicy:
  enabled: false

livenessProbe:
  enabled: false

customLivenessProbe:
  httpGet:
    path: /health/live
    port: ${local.kc_target_http_port}
    scheme: HTTP
  initialDelaySeconds: 0
  periodSeconds: 1
  timeoutSeconds: 5
  failureThreshold: 3
  successThreshold: 1

readinessProbe:
  enabled: false

customReadinessProbe:
  httpGet:
    path: /health/ready
    port: ${local.kc_target_http_port}
    scheme: HTTP
  initialDelaySeconds: 0
  periodSeconds: 10
  timeoutSeconds: 30
  failureThreshold: 3
  successThreshold: 1

startupProbe:
  enabled: false

customStartupProbe:
  httpGet:
    path: /health/started
    port: ${local.kc_target_http_port}
    scheme: HTTP
  initialDelaySeconds: 30
  periodSeconds: 5
  timeoutSeconds: 1
  failureThreshold: 60
  successThreshold: 1

ingress:
  enabled: true
  hostname: ${local.keycloak_url}
  ingressClassName: ""
  pathType: ImplementationSpecific
  path: /*
  annotations:
    kubernetes.io/ingress.class: alb
    alb.ingress.kubernetes.io/scheme: internet-facing
    alb.ingress.kubernetes.io/group.name: "${local.group_name}"
    alb.ingress.kubernetes.io/listen-ports: '[{"HTTP":80}]'
    alb.ingress.kubernetes.io/success-codes: 200-399
    alb.ingress.kubernetes.io/healthcheck-path: /health/ready
    alb.ingress.kubernetes.io/healthcheck-port: "${local.kc_target_http_port}"

initdbScripts:
  kc_init_script.sh: |
    #!/usr/bin/env bash
    sleep 20
    /opt/keycloak/bin/kcadm.sh config credentials --realm master --user "${KEYCLOAK_ADMIN}" --password "${KEYCLOAK_ADMIN_PASSWORD}" --server "http://localhost:8080"
    /opt/keycloak/bin/kcadm.sh update realms/master -s sslRequired=NONE
EOF
  ]
}
