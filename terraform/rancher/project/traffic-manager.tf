resource "helm_release" "traffic-manager" {
  count     = 1
  namespace = rancher2_namespace.this.name
  name      = "traffic-manager"
  repository = local.catalogs.wiredata
  chart     = "telepresence"
  version   = "2.20.1"
  values = [<<-EOF
managerRbac:
  create: true
  namespaced: true
  namespaces:
  - ${rancher2_namespace.this.name}
    EOF
  ]
}
