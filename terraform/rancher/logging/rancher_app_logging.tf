resource "rancher2_app_v2" "rancher-logging" {  
  cluster_id = var.rancher_cluster_name
  name = "rancher-logging"
  namespace = "cattle-logging-system"
  repo_name = "rancher-charts"
  chart_name = "rancher-logging"
  chart_version = "3.6.000"
}