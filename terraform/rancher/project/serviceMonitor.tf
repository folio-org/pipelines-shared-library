# Create ServiceMonitor for monitoring jvm metrics on 9991 port
resource "kubectl_manifest" "service_monitor" {
  provider           = kubectl
  override_namespace = rancher2_namespace.this.name
  yaml_body          = <<YAML
apiVersion: monitoring.coreos.com/v1
kind: ServiceMonitor
metadata:
  name: ${var.rancher_project_name}-folio-metrics
spec:
  endpoints:
  - interval: 30s
    path: /metrics
    scheme: http
    targetPort: 9991
  jobLabel: folio
  namespaceSelector:
    matchNames:
    - ${var.rancher_project_name}
  selector:
    matchExpressions:
    - key: app.kubernetes.io/name
      operator: Exists
  YAML
}

# Create ServiceMonitor for monitoring Openearch metrics
resource "kubectl_manifest" "service_monitor_opensearch" {
  provider           = kubectl
  override_namespace = rancher2_namespace.this.name
  yaml_body          = <<YAML
apiVersion: monitoring.coreos.com/v1
kind: ServiceMonitor
metadata:
  name: ${var.rancher_project_name}-opensearch-mertics
spec:
  endpoints:
  - interval: 30s
    path: /_prometheus/metrics
    scheme: http
    targetPort: 9200
  jobLabel: opensearch
  namespaceSelector:
    matchNames:
    - ${var.rancher_project_name}
  selector:
    matchLabels:
      app.kubernetes.io/name: opensearch
  YAML
}