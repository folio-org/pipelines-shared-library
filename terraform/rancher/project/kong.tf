resource "helm_release" "kong" {
  count      = var.eureka ? 1 : 0
  chart      = "kong"
  depends_on = [rancher2_secret.db-credentials]
  name       = "kong-${var.rancher_project_name}"
  namespace  = rancher2_namespace.this.id
  version    = "12.0.11"
  repository = "https://repository.folio.org/repository/helm-bitnami-proxy"
  values = [
    <<-EOF
TBD...
EOF
  ]
}
