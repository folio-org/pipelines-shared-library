resource "random_integer" "node_port" {
  max = 32767
  min = 30000

}

resource "helm_release" "kong" {
  count      = var.eureka ? 1 : 0
  chart      = "kong"
  depends_on = [rancher2_secret.db-credentials, random_integer.node_port, helm_release.postgresql, helm_release.pgadmin, postgresql_database.kong, postgresql_role.kong]
  name       = "kong-${var.rancher_project_name}"
  namespace  = rancher2_namespace.this.id
  version    = "12.0.11"
  repository = "https://repository.folio.org/repository/helm-bitnami-proxy"
  values = [<<-EOF
image:
  registry: 732722833398.dkr.ecr.us-west-2.amazonaws.com
  repository: kong
  tag: testing
  pullPolicy: IfNotPresent
database: postgresql
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
migrations:
  annotations:
    helm.sh/hook: post-install, pre-upgrade
    helm.sh/hook-delete-policy: before-hook-creation,hook-succeeded
EOF
  ]
}
