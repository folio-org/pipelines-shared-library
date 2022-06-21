# Create a new rancher2 frontend app in a default Project namespace
resource "rancher2_app" "frontend" {
  project_id       = rancher2_project.this.id
  target_namespace = rancher2_namespace.this.name
  catalog_name     = local.folio_helm_catalog_name
  name             = "platform-complete"
  description      = "UI app"
  template_name    = "platform-complete"
  force_upgrade    = "true"
  answers = {
    "image.tag"                                                         = var.frontend_image_tag
    "service.type"                                                      = "NodePort"
    "ingress.enabled"                                                   = "true"
    "ingress.annotations.kubernetes\\.io/ingress\\.class"               = "alb"
    "ingress.annotations.alb\\.ingress\\.kubernetes\\.io/scheme"        = "internet-facing"
    "ingress.annotations.alb\\.ingress\\.kubernetes\\.io/group\\.name"  = local.group_name
    "ingress.annotations.alb\\.ingress\\.kubernetes\\.io/listen-ports"  = "[{\"HTTPS\":443}]"
    "ingress.annotations.alb\\.ingress\\.kubernetes\\.io/success-codes" = "200-399"
    "ingress.hosts[0].paths[0]"                                         = "/*"
    "ingress.hosts[0].host"                                             = local.frontend_url
    "postJob.enabled"                                                   = "false"
  }
}
