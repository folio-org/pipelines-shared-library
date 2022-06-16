# Create a new Rancher2 Catalog V2 using git repo and branch
resource "rancher2_catalog" "aws-ebs-csi-driver" {
  depends_on = [helm_release.rancher, null_resource.patch]
  name       = "aws-ebs-csi-driver"
  url        = "https://kubernetes-sigs.github.io/aws-ebs-csi-driver"

}
resource "rancher2_catalog" "bitnami" {
  depends_on = [helm_release.rancher, null_resource.patch]
  name       = "bitnami"
  url        = "https://repository.folio.org/repository/helm-bitnami-proxy/"

}
resource "rancher2_catalog" "folio-helm-repo" {
  depends_on = [helm_release.rancher, null_resource.patch]
  name       = "folio-helm-repo"
  url        = "https://repository.folio.org/repository/helm-hosted/"

}
resource "rancher2_catalog" "grafana" {
  depends_on = [helm_release.rancher, null_resource.patch]
  name       = "grafana"
  url        = "https://grafana.github.io/helm-charts"

}
resource "rancher2_catalog" "helm" {
  depends_on = [helm_release.rancher, null_resource.patch]
  name       = "helm"
  url        = "https://charts.helm.sh/stable"

}
resource "rancher2_catalog" "helm-incubator" {
  depends_on = [helm_release.rancher, null_resource.patch]
  name       = "helm-incubator"
  url        = "https://charts.helm.sh/incubator"

}
resource "rancher2_catalog" "helm3-library" {
  depends_on = [helm_release.rancher, null_resource.patch]
  name       = "helm3-library"
  url        = "https://git.rancher.io/helm3-charts"

}
resource "rancher2_catalog" "influx" {
  depends_on = [helm_release.rancher, null_resource.patch]
  name       = "influx"
  url        = "https://helm.influxdata.com/"

}
resource "rancher2_catalog" "library" {
  depends_on = [helm_release.rancher, null_resource.patch]
  name       = "library"
  url        = "https://git.rancher.io/charts"

}
resource "rancher2_catalog" "runix" {
  depends_on = [helm_release.rancher, null_resource.patch]
  name       = "runix"
  url        = "https://helm.runix.net"

}
resource "rancher2_catalog" "system-library" {
  depends_on = [helm_release.rancher, null_resource.patch]
  name       = "system-library"
  url        = "https://git.rancher.io/system-charts"
}
