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

# Minio deployment
resource "helm_release" "minio" {
  count      = var.s3_embedded ? 1 : 0
  namespace  = rancher2_namespace.this.name
  repository = local.catalogs.bitnami
  name       = "minio"
  chart      = "minio"
  version    = "11.8.1"
  values = [<<-EOF
defaultBuckets: ${local.s3_bucket_name},local-files
image:
  repository: minio
  registry: 732722833398.dkr.ecr.us-west-2.amazonaws.com
  tag: 2022.8.11-debian-11-r0
  pullPolicy: IfNotPresent
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
${local.schedule_value}
EOF
  ]
}

resource "rancher2_secret" "s3-credentials" {
  name         = "s3-credentials"
  project_id   = rancher2_project.this.id
  namespace_id = rancher2_namespace.this.name
  data = {

    LOCAL_FS_COMPOSE_WITH_AWS_SDK = base64encode(var.s3_embedded ? "false" : "true")
    LOCAL_FS_URL                  = base64encode(var.s3_embedded ? join("", ["https://", local.minio_url]) : "https://s3.amazonaws.com")
    LOCAL_FS_REGION               = base64encode(var.aws_region)
    LOCAL_FS_BUCKET               = base64encode("local-files")
    LOCAL_FS_ACCESS_KEY_ID        = base64encode(var.s3_embedded ? random_string.access_key[0].result : var.s3_access_key)
    LOCAL_FS_SECRET_ACCESS_KEY    = base64encode(var.s3_embedded ? random_password.secret_access_key[0].result : var.s3_secret_key)

    S3_IS_AWS            = base64encode(var.s3_embedded ? "false" : "true")
    S3_URL               = base64encode(var.s3_embedded ? join("", ["https://", local.minio_url]) : "https://s3.amazonaws.com")
    S3_REGION            = base64encode(var.aws_region)
    S3_BUCKET            = base64encode(local.s3_bucket_name)
    S3_ACCESS_KEY_ID     = base64encode(var.s3_embedded ? random_string.access_key[0].result : var.s3_access_key)
    S3_SECRET_ACCESS_KEY = base64encode(var.s3_embedded ? random_password.secret_access_key[0].result : var.s3_secret_key)

    GLOBAL_S3_SECRET_KEY = base64encode(var.s3_embedded ? random_password.secret_access_key[0].result : var.s3_secret_key)
  }
}

resource "null_resource" "s3-bucket-cleanup" {
  count      = var.s3_embedded ? 0 : 1
  depends_on = [aws_s3_bucket.s3-bucket-for-backend-modules]

  triggers = {
    bucket = local.s3_bucket_name
    region = var.aws_region
  }

  provisioner "local-exec" {
    when        = destroy
    interpreter = ["/bin/bash", "-c"]
    environment = {
      BUCKET = self.triggers.bucket
      REGION = self.triggers.region
    }
    command = <<-EOT
      aws s3 rm "s3://$BUCKET" --recursive --region "$REGION" || true

      versions=$(aws s3api list-object-versions --bucket "$BUCKET" --region "$REGION" --query 'Versions[].[Key,VersionId]' --output text || true)
      if [ -n "$versions" ] && [ "$versions" != "None" ]; then
        while IFS=$'\t' read -r key version; do
          if [ -n "$key" ] && [ -n "$version" ] && [ "$key" != "None" ] && [ "$version" != "None" ]; then
            aws s3api delete-object --bucket "$BUCKET" --key "$key" --version-id "$version" --region "$REGION" || true
          fi
        done <<< "$versions"
      fi

      markers=$(aws s3api list-object-versions --bucket "$BUCKET" --region "$REGION" --query 'DeleteMarkers[].[Key,VersionId]' --output text || true)
      if [ -n "$markers" ] && [ "$markers" != "None" ]; then
        while IFS=$'\t' read -r key version; do
          if [ -n "$key" ] && [ -n "$version" ] && [ "$key" != "None" ] && [ "$version" != "None" ]; then
            aws s3api delete-object --bucket "$BUCKET" --key "$key" --version-id "$version" --region "$REGION" || true
          fi
        done <<< "$markers"
      fi

      for _ in $(seq 1 24); do
        obj_count=$(aws s3api list-objects-v2 --bucket "$BUCKET" --region "$REGION" --query 'KeyCount' --output text 2>/dev/null || echo 0)
        ver_count=$(aws s3api list-object-versions --bucket "$BUCKET" --region "$REGION" --query 'length(Versions)' --output text 2>/dev/null || echo 0)
        marker_count=$(aws s3api list-object-versions --bucket "$BUCKET" --region "$REGION" --query 'length(DeleteMarkers)' --output text 2>/dev/null || echo 0)

        [ "$obj_count" = "None" ] && obj_count=0
        [ "$ver_count" = "None" ] && ver_count=0
        [ "$marker_count" = "None" ] && marker_count=0

        if [ "$obj_count" = "0" ] && [ "$ver_count" = "0" ] && [ "$marker_count" = "0" ]; then
          exit 0
        fi

        aws s3 rm "s3://$BUCKET" --recursive --region "$REGION" || true
        sleep 5
      done

      exit 0
    EOT
  }
}

resource "aws_s3_bucket" "s3-bucket-for-backend-modules" {
  count         = var.s3_embedded ? 0 : 1
  bucket        = local.s3_bucket_name
  force_destroy = true
  tags = {
    Cluster   = data.rancher2_cluster.this.name
    Project   = var.rancher_project_name
    Name      = rancher2_namespace.this.name
    ManagedBy = "Terraform"
  }
}
