# Create ServiceMonitor for monitoring jvm metrics on 9991 port
resource "kubectl_manifest" "service_monitor" {
  count              = var.register_in_rancher ? 1 : 0
  provider           = kubectl
  override_namespace = rancher2_namespace.this.name
  yaml_body          = <<YAML
apiVersion: monitoring.coreos.com/v1
kind: ServiceMonitor
metadata:
  name: ${var.rancher_project_name}-metrics
spec:
  endpoints:
  - interval: 30s
    path: /metrics
    scheme: http
    targetPort: 9991
  jobLabel: app
  namespaceSelector:
    matchNames:
    - ${var.rancher_project_name}
  selector:
    matchExpressions:
    - key: app.kubernetes.io/name
      operator: Exists
  YAML
}
