resource "helm_release" "traffic-manager" {
  count      = 1
  namespace  = rancher2_namespace.this.name
  name       = "traffic-manager-${rancher2_namespace.this.name}"
  repository = local.catalogs.telepresence
  chart      = "telepresence-oss"
  version    = "2.20.1"
  values = [<<-EOF
managerRbac:
  create: true
  namespaced: true
  namespaces:
  - ${rancher2_namespace.this.name}
    EOF
  ]
}
