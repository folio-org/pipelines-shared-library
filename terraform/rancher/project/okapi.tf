# Create rancher2 OKAPI app in Project namespace
resource "rancher2_app_v2" "okapi" {
  depends_on    = [time_sleep.wait_for_db, rancher2_app_v2.kafka, rancher2_app_v2.elasticsearch, rancher2_app_v2.minio, module.rds, aws_msk_cluster.this, module.aws_es]
  cluster_id    = data.rancher2_cluster.this.id
  namespace     = rancher2_namespace.this.name
  name          = "okapi"
  repo_name     = local.folio_helm_repo_name
  chart_name    = "okapi"
  force_upgrade = "true"
  values        = <<-EOT
    image:
      repository: ${join("/", [length(regexall(".*SNAPSHOT.*", var.okapi_version)) > 0 ? "folioci" : "folioorg", "okapi"])}
      tag: ${var.okapi_version}
    resources:
      requests:
        memory: ${try(local.helm_configs["okapi"]["resources"]["requests"]["memory"])}
      limits:
        memory: ${try(local.helm_configs["okapi"]["resources"]["limits"]["memory"])}
    service:
      type: NodePort
    ingress:
      enabled: true
      hosts:
        - host: ${local.okapi_url}
          paths:
            - /*
      annotations:
        kubernetes.io/ingress.class: alb
        alb.ingress.kubernetes.io/scheme: internet-facing
        alb.ingress.kubernetes.io/group.name: ${local.group_name}
        alb.ingress.kubernetes.io/listen-ports: '[{"HTTPS":443}]'
        alb.ingress.kubernetes.io/success-codes: '200-403'
        alb.ingress.kubernetes.io/healthcheck-path: /_/version
        alb.ingress.kubernetes.io/load-balancer-attributes: idle_timeout.timeout_seconds=4000
    postJob:
      enabled: false
    javaOptions: ${try(local.helm_configs["okapi"]["javaOptions"])}
    replicaCount: ${try(local.helm_configs["okapi"]["replicaCount"])}
  EOT
}
