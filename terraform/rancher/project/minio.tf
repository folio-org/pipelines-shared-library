resource "random_string" "access_key" {
  count   = var.s3_embedded ? 1 : 0
  length  = 20
  lower   = false
  numeric = false
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
  chart_version = "11.8.1"
  force_upgrade = "true"
  values        = <<-EOT
    defaultBuckets: ${join(",", [local.s3_buckets_string, "local-files"])}
    auth:
      rootUser: ${random_string.access_key[0].result}
      rootPassword: ${random_password.secret_access_key[0].result}
    resources:
      limits:
        memory: 1536Mi
    persistence:
      size: 10Gi
    extraEnvVars:
    - name: MINIO_SERVER_URL
      value: https://${local.minio_url}
    - name: MINIO_BROWSER_REDIRECT_URL
      value: https://${local.minio_console_url}
    service:
      type: NodePort
    ingress:
      enabled: true
      hostname: ${local.minio_console_url}
      path: /*
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
      path: /*
      annotations:
        kubernetes.io/ingress.class: alb
        alb.ingress.kubernetes.io/scheme: internet-facing
        alb.ingress.kubernetes.io/group.name: ${local.group_name}
        alb.ingress.kubernetes.io/listen-ports: '[{"HTTPS":443}]'
        alb.ingress.kubernetes.io/success-codes: '200-399'
        alb.ingress.kubernetes.io/healthcheck-path: /minio/health/live
        alb.ingress.kubernetes.io/load-balancer-attributes: idle_timeout.timeout_seconds=4000
  EOT
}

resource "rancher2_secret" "s3-credentials" {
  for_each = local.s3_buckets_map

  name         = "s3-${each.key}-credentials"
  project_id   = rancher2_project.this.id
  namespace_id = rancher2_namespace.this.name
  data = {
    AWS_SDK               = base64encode(var.s3_embedded ? "false" : "true")
    AWS_URL               = base64encode(var.s3_embedded ? join("", ["https://", local.minio_url]) : "https://s3.amazonaws.com")
    AWS_REGION            = base64encode(var.aws_region)
    AWS_BUCKET            = base64encode(each.value)
    AWS_ACCESS_KEY_ID     = base64encode(var.s3_embedded ? random_string.access_key[0].result : var.s3_access_key)
    AWS_SECRET_ACCESS_KEY = base64encode(var.s3_embedded ? random_password.secret_access_key[0].result : var.s3_secret_key)

    LOCAL_FS_COMPOSE_WITH_AWS_SDK = base64encode(var.s3_embedded ? "false" : "true")
    LOCAL_FS_URL                  = base64encode(var.s3_embedded ? join("", ["https://", local.minio_url]) : "https://s3.amazonaws.com")
    LOCAL_FS_REGION               = base64encode(var.aws_region)
    LOCAL_FS_BUCKET               = base64encode("local-files")
    LOCAL_FS_ACCESS_KEY_ID        = base64encode(var.s3_embedded ? random_string.access_key[0].result : var.s3_access_key)
    LOCAL_FS_SECRET_ACCESS_KEY    = base64encode(var.s3_embedded ? random_password.secret_access_key[0].result : var.s3_secret_key)

    USE_AWS_SDK          = base64encode(var.s3_embedded ? "false" : "true")
    S3_IS_AWS            = base64encode(var.s3_embedded ? "false" : "true")
    S3_URL               = base64encode(var.s3_embedded ? join("", ["https://", local.minio_url]) : "https://s3.amazonaws.com")
    S3_REGION            = base64encode(var.aws_region)
    S3_BUCKET            = base64encode(each.value)
    S3_ACCESS_KEY_ID     = base64encode(var.s3_embedded ? random_string.access_key[0].result : var.s3_access_key)
    S3_SECRET_ACCESS_KEY = base64encode(var.s3_embedded ? random_password.secret_access_key[0].result : var.s3_secret_key)

    LIST_APP_S3_BUCKET = base64encode(each.value)
  }
}
