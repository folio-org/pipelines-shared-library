resource "random_string" "access_key" {
  count   = var.s3_embedded ? 1 : 0
  length  = 20
  lower   = false
  number  = false
  special = false
}

resource "random_password" "secret_access_key" {
  count            = var.s3_embedded ? 1 : 0
  length           = 40
  min_special      = 1
  override_special = "/"
}

# Create rancher2 Minio app in Project namespace
resource "rancher2_app_v2" "minio" {
  count         = var.s3_embedded ? 1 : 0
  cluster_id    = data.rancher2_cluster.this.id
  namespace     = rancher2_namespace.this.name
  name          = "minio"
  repo_name     = "bitnami"
  chart_name    = "minio"
  chart_version = "11.5.2"
  force_upgrade = "true"
  values = <<-EOT
    defaultBuckets: ${join(",", [
  join("-", [data.rancher2_cluster.this.name, var.rancher_project_name, "data-export"]),
  join("-", [data.rancher2_cluster.this.name, var.rancher_project_name, "data-worker"]),
])}
    auth:
      rootUser: ${random_string.access_key[0].result}
      rootPassword: ${random_password.secret_access_key[0].result}
    resources:
      limits:
        memory: 1024Mi
    persistence:
      size: 10Gi
    service:
      type: NodePort
    ingress:
      enabled: true
      hostname: ${local.minio_console_url}
      annotations:
        kubernetes.io/ingress.class: alb
        alb.ingress.kubernetes.io/scheme: internet-facing
        alb.ingress.kubernetes.io/group.name: ${local.group_name}
        alb.ingress.kubernetes.io/listen-ports: '[{"HTTPS":443}]'
        alb.ingress.kubernetes.io/success-codes: 200-399
        alb.ingress.kubernetes.io/healthcheck-path: /
        alb.ingress.kubernetes.io/load-balancer-attributes: idle_timeout.timeout_seconds=4000
    apiIngress:
      enabled: true
      hostname: ${local.minio_url}
      annotations:
        kubernetes.io/ingress.class: alb
        alb.ingress.kubernetes.io/scheme: internet-facing
        alb.ingress.kubernetes.io/group.name: ${local.group_name}
        alb.ingress.kubernetes.io/listen-ports: '[{"HTTPS":443}]'
        alb.ingress.kubernetes.io/success-codes: 200-399
        alb.ingress.kubernetes.io/healthcheck-path: /
        alb.ingress.kubernetes.io/load-balancer-attributes: idle_timeout.timeout_seconds=4000
  EOT
}
