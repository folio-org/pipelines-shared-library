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

resource "helm_release" "keycloak" {
  count        = (var.eureka ? 1 : 0)
  chart        = "keycloak"
  depends_on   = [rancher2_secret.keycloak-credentials, helm_release.postgresql]
  name         = "keycloak-${var.rancher_project_name}"
  namespace    = rancher2_namespace.this.id
  version      = "21.0.4"
  force_update = false
  repository   = "https://repository.folio.org/repository/helm-bitnami-proxy"
  values = [
    <<-EOF
image:
  registry: folioci
  repository: folio-keycloak
  tag: latest
  pullPolicy: Always
  debug: false

auth:
  adminUser: "admin"
  existingSecret: keycloak-credentials
  passwordSecretKey: KEYCLOAK_ADMIN_PASSWORD

extraEnvVars:
  - name: KC_HOSTNAME_BACKCHANNEL_DYNAMIC
    value: "true"
  - name: KC_HOSTNAME
    value: "https://${local.keycloak_url}"
  - name: KC_HOSTNAME_BACKCHANNEL
    value: "http://keycloak-${rancher2_namespace.this.id}-headless.${rancher2_namespace.this.id}.svc.cluster.local"
  - name: KC_HOSTNAME_STRICT
    value: "false"
  - name: KC_HOSTNAME_STRICT_HTTPS
    value: "false"
  - name: KC_PROXY
    value: "edge"
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
  - name: KC_LOG_LEVEL
    value: "DEBUG"
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
  - name: KC_HTTP_ENABLED
    value: "true"
  - name: KC_HTTP_PORT
    value: "8080"
  - name: KC_HEALTH_ENABLED
    value: "true"

resources:
  requests:
    cpu: 512m
    memory: 2Gi
  limits:
    cpu: 2048m
    memory: 3Gi

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

enableDefaultInitContainers: false

containerSecurityContext:
  enabled: false

service:
  type: NodePort
  http:
    enabled: true
  ports:
    http: 8080

networkPolicy:
  enabled: false

livenessProbe:
  enabled: false

readinessProbe:
  enabled: false

startupProbe:
  enabled: false

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
    alb.ingress.kubernetes.io/listen-ports: '[{"HTTPS":443}]'
    alb.ingress.kubernetes.io/success-codes: 200-399
    alb.ingress.kubernetes.io/healthcheck-path: /
EOF
  ]
}
