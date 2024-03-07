resource "rancher2_secret" "opensearch-credentials" {
  name         = "opensearch-credentials"
  project_id   = rancher2_project.this.id
  namespace_id = rancher2_namespace.this.id
  data = {
    ENV                    = base64encode(local.env_name)
    ELASTICSEARCH_URL      = base64encode(var.opensearch_shared ? base64decode(local.opensearch_value["ELASTICSEARCH_URL"]) : "http://opensearch-${var.rancher_project_name}:9200")
    ELASTICSEARCH_HOST     = base64encode(var.opensearch_shared ? base64decode(local.opensearch_value["ELASTICSEARCH_HOST"]) : "")
    ELASTICSEARCH_PORT     = base64encode(var.opensearch_shared ? base64decode(local.opensearch_value["ELASTICSEARCH_PORT"]) : "9200")
    ELASTICSEARCH_USERNAME = base64encode(var.opensearch_shared ? base64decode(local.opensearch_value["ELASTICSEARCH_USERNAME"]) : "admin")
    ELASTICSEARCH_PASSWORD = base64encode(var.opensearch_shared ? base64decode(local.opensearch_value["ELASTICSEARCH_PASSWORD"]) : "admin")
    OPENSEARCH_URL         = base64encode(var.opensearch_shared ? base64decode(local.opensearch_value["ELASTICSEARCH_URL"]) : "http://opensearch-${var.rancher_project_name}:9200")
    OPENSEARCH_HOST        = base64encode(var.opensearch_shared ? base64decode(local.opensearch_value["ELASTICSEARCH_HOST"]) : "")
    OPENSEARCH_PORT        = base64encode(var.opensearch_shared ? base64decode(local.opensearch_value["ELASTICSEARCH_PORT"]) : "9200")
    OPENSEARCH_USERNAME    = base64encode(var.opensearch_shared ? base64decode(local.opensearch_value["ELASTICSEARCH_USERNAME"]) : "admin")
    OPENSEARCH_PASSWORD    = base64encode(var.opensearch_shared ? base64decode(local.opensearch_value["ELASTICSEARCH_PASSWORD"]) : "admin")
  }
}

# Opensearch master deployment
resource "helm_release" "opensearch-master" {
  count      = var.opensearch_shared ? 0 : 1
  namespace  = rancher2_namespace.this.name
  repository = "https://opensearch-project.github.io/helm-charts"
  name       = "opensearch-master"
  chart      = "opensearch"
  version    = "1.14.0"
  values = [<<-EOF
clusterName: "opensearch-${var.rancher_project_name}"
masterService: "opensearch-${var.rancher_project_name}"
nodeGroup: "master"
replicas: 1
roles:
  - master
extraEnvs:
  - name: DISABLE_SECURITY_PLUGIN
    value: "true"
resources:
  requests:
    memory: 1536Mi
  limits:
    memory: 2048Mi
plugins:
  enabled: true
  installList: [analysis-icu, analysis-kuromoji, analysis-smartcn, analysis-nori, analysis-phonetic, https://github.com/aiven/prometheus-exporter-plugin-for-opensearch/releases/download/1.3.5.0/prometheus-exporter-1.3.5.0.zip]
EOF
  ]
}

# Opensearch data deployment
resource "helm_release" "opensearch-data" {
  count      = var.opensearch_shared ? 0 : 1
  namespace  = rancher2_namespace.this.name
  repository = "https://opensearch-project.github.io/helm-charts"
  name       = "opensearch-data"
  chart      = "opensearch"
  version    = "1.14.0"
  values = [<<-EOF
clusterName: "opensearch-${var.rancher_project_name}"
masterService: "opensearch-${var.rancher_project_name}"
nodeGroup: "data"
replicas: 1
roles:
  - data
extraEnvs:
  - name: DISABLE_SECURITY_PLUGIN
    value: "true"
resources:
  requests:
    memory: 1536Mi
  limits:
    memory: 2048Mi
persistence:
  size: ${join("", [var.es_ebs_volume_size, "Gi"])}
plugins:
  enabled: true
  installList: [analysis-icu, analysis-kuromoji, analysis-smartcn, analysis-nori, analysis-phonetic, https://github.com/aiven/prometheus-exporter-plugin-for-opensearch/releases/download/1.3.5.0/prometheus-exporter-1.3.5.0.zip]
EOF
  ]
}

# Opensearch client deployment
resource "helm_release" "opensearch-client" {
  count      = var.opensearch_shared ? 0 : 1
  namespace  = rancher2_namespace.this.name
  repository = "https://opensearch-project.github.io/helm-charts"
  name       = "opensearch-client"
  chart      = "opensearch"
  version    = "1.14.0"
  values = [<<-EOF
service:
  type: NodePort
clusterName: "opensearch-${var.rancher_project_name}"
masterService: "opensearch-${var.rancher_project_name}"
nodeGroup: "client"
replicas: 1
roles:
  - remote_cluster_client
persistence:
  enabled: false
extraEnvs:
  - name: DISABLE_SECURITY_PLUGIN
    value: "true"
resources:
  requests:
    memory: 1024Mi
  limits:
    memory: 1536Mi
plugins:
  enabled: true
  installList: [analysis-icu, analysis-kuromoji, analysis-smartcn, analysis-nori, analysis-phonetic, https://github.com/aiven/prometheus-exporter-plugin-for-opensearch/releases/download/1.3.5.0/prometheus-exporter-1.3.5.0.zip]
ingress:
  hosts:
    - ${join(".", [join("-", [data.rancher2_cluster.this.name, var.rancher_project_name, "opensearch-client"]), var.root_domain])}
  path: /
  enabled: true
  annotations:
    kubernetes.io/ingress.class: alb
    alb.ingress.kubernetes.io/scheme: internet-facing
    alb.ingress.kubernetes.io/group.name: ${local.group_name}
    alb.ingress.kubernetes.io/listen-ports: '[{"HTTPS":443}]'
    alb.ingress.kubernetes.io/success-codes: 200-399
EOF
  ]
}

# Opensearch dashboards deployment
resource "helm_release" "opensearch-dashboards" {
  count      = var.s3_embedded ? 1 : 0
  namespace  = rancher2_namespace.this.name
  repository = "https://opensearch-project.github.io/helm-charts"
  name       = "opensearch-dashboards"
  chart      = "opensearch-dashboards"
  version    = "1.4.1"
  values = [<<-EOF
service:
  type: NodePort
clusterName: "opensearch-${var.rancher_project_name}"
masterService: "opensearch-${var.rancher_project_name}"
replicas: 1
opensearchHosts: "http://opensearch-${var.rancher_project_name}:9200"
extraEnvs:
  - name: DISABLE_SECURITY_DASHBOARDS_PLUGIN
    value: "true"
  - name: OPENSEARCH_SSL_VERIFICATIONMODE
    value: "none"
  - name: OPENSEARCH_USERNAME
    value: "admin"
  - name: OPENSEARCH_PASSWORD
    value: "admin"
resources:
  requests:
    memory: 2048Mi
  limits:
    memory: 3072Mi
ingress:
  enabled: true
  hosts:
    - host: ${join(".", [join("-", [data.rancher2_cluster.this.name, var.rancher_project_name, "opensearch-dashboards"]), var.root_domain])}
      paths:
        - path: /
  annotations:
    kubernetes.io/ingress.class: alb
    alb.ingress.kubernetes.io/scheme: internet-facing
    alb.ingress.kubernetes.io/group.name: ${local.group_name}
    alb.ingress.kubernetes.io/listen-ports: '[{"HTTPS":443}]'
    alb.ingress.kubernetes.io/success-codes: 200-399
EOF
  ]
}
