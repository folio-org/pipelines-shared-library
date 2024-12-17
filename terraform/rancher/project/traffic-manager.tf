resource "helm_release" "traffic-manager" {
  count     = 1
  namespace = rancher2_namespace.this.name
  name      = "traffic-manager-${rancher2_namespace.this.name}"
  chart     = "oci://ghcr.io/telepresenceio/telepresence-oss"
  version   = "2.21.1"
  values = [<<-EOF
managerRbac:
  create: true
  namespaced: true
  namespaces:
  - ${rancher2_namespace.this.name}
    EOF
  ]
}
