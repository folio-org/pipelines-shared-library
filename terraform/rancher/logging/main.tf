#Create the cluster role binding to give the user the privileges to create resources into Kubernetes
resource "kubernetes_cluster_role_binding" "cluster-admin-binding" {

  metadata {
    name = "cluster-role-binding"
  }
  role_ref {
    api_group = "rbac.authorization.k8s.io"
    kind      = "ClusterRole"
    name      = "cluster-admin"
  }
  subject {
    kind      = "User"
    name      = "${var.email}"
    api_group = "rbac.authorization.k8s.io"
  }
  subject {
    kind      = "ServiceAccount"
    name      = "default"
    namespace = "kube-system"
  }
  subject {
    kind      = "Group"
    name      = "system:masters"
    api_group = "rbac.authorization.k8s.io"
  }

  depends_on = [rancher2_app_v2.rancher-logging]

}

# Install ECK operator via helm-charts
resource "helm_release" "elastic" {
  provider         = helm.cluster
  name             = "logging-operator"
  repository       = "https://kubernetes-charts.banzaicloud.com"
  chart            = "logging-operator"
  namespace        = "logging"
  create_namespace = "true"

  depends_on = [kubernetes_cluster_role_binding.cluster-admin-binding]

}

# Delay of 30s to wait until ECK operator is up and running
resource "time_sleep" "wait_30_seconds" {
  depends_on = [helm_release.elastic]

  create_duration = "30s"
}

# Create Elasticsearch manifest
resource "kubectl_manifest" "elastic_quickstart" {
    yaml_body = <<YAML
apiVersion: elasticsearch.k8s.elastic.co/v1
kind: Elasticsearch
metadata:
  name: quickstart
  namespace: logging
spec:
  version: 8.3.2
  nodeSets:
  - name: default
    count: 3
    config:
      node.master: true
      node.data: true
      node.ingest: true
      node.store.allow_mmap: false
YAML
provider = kubectl
  # provisioner "local-exec" {
  #    command = "sleep 60"
  # }
  depends_on = [helm_release.elastic, time_sleep.wait_30_seconds]
}

# Create Kibana resource
resource "kubectl_manifest" "kibana_quickstart" {
    yaml_body = <<YAML
apiVersion: kibana.k8s.elastic.co/v1
kind: Kibana
metadata:
  name: quickstart
  namespace: logging
spec:
  version: 7.10.0
  count: 1
  elasticsearchRef:
    name: quickstart
YAML

  provisioner "local-exec" {
     command = "sleep 60"
  }
  depends_on = [helm_release.elastic, kubectl_manifest.elastic_quickstart]
}

