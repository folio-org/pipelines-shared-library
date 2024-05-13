resource "rancher2_app_v2" "keycloak" {
  count         = var.eureka ? 1 : 0
  cluster_id    = data.rancher2_cluster.this.id
  namespace     = rancher2_namespace.this.name
  name          = "keycloak"
  repo_name     = "bitnami"
  chart_name    = "keycloak"
  chart_version = "21.1.0"
  force_upgrade = true
  values        = <<-EOT
  service:
    type: NodePort
  ingress:
    enabled: true
    hostname: ${join(".", [join("-", [data.rancher2_cluster.this.name, var.rancher_project_name, "keycloak"]), var.root_domain])}
    path: /*
    annotations:
      kubernetes.io/ingress.class: alb
      alb.ingress.kubernetes.io/scheme: internet-facing
      alb.ingress.kubernetes.io/group.name: ${local.group_name}
      alb.ingress.kubernetes.io/listen-ports: '[{"HTTPS":443}]'
      alb.ingress.kubernetes.io/success-codes: 200-399
      alb.ingress.kubernetes.io/healthcheck-path: /health/ready
      alb.ingress.kubernetes.io/healthcheck-port: '80'
    auth:
      adminUser: ${var.keycloak_config_map.admin_user}
      adminPassword: ${var.keycloak_config_map.admin_password}
    postgresql:
      enabled=false
    externalDatabase:
      host: ${var.pg_embedded ? local.pg_service_writer : module.rds[0].cluster_endpoint}
      port: ${var.keycloak_config_map.db_port}
      user: ${var.keycloak_config_map.db_user}
      database: ${var.keycloak_config_map.db_name}
      existingSecret: ${var.keycloak_config_map.db_secret}
    extraEnvVars:
      - name: KEYCLOAK_LOG_LEVEL
        value: DEBUG
  EOT
}
