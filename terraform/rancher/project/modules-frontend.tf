# Create a new rancher2 frontend app in a default Project namespace
resource "rancher2_app_v2" "frontend" {
  depends_on    = [rancher2_app_v2.okapi]
  cluster_id    = data.rancher2_cluster.this.id
  namespace     = rancher2_namespace.this.name
  name          = "platform-complete"
  repo_name     = local.folio_helm_repo_name
  chart_name    = "platform-complete"
  force_upgrade = true
  values        = <<-EOT
    image:
      tag: ${var.frontend_image_tag}
    service:
      type: NodePort
    ingress:
      enabled: true
      hosts:
        - host: ${local.frontend_url}
          paths:
            - /*
      annotations:
        kubernetes.io/ingress.class: alb
        alb.ingress.kubernetes.io/scheme: internet-facing
        alb.ingress.kubernetes.io/group.name: ${local.group_name}
        alb.ingress.kubernetes.io/listen-ports: '[{"HTTPS":443}]'
        alb.ingress.kubernetes.io/success-codes: 200-399
    postJob:
      enabled: false

  EOT
}
