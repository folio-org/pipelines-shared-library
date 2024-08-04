#Creating a monitoring project in Rancher.
resource "rancher2_project" "monitoring" {
  count                     = var.register_in_rancher && var.enable_monitoring ? 1 : 0
  provider                  = rancher2
  name                      = "monitoring"
  cluster_id                = rancher2_cluster_sync.this[0].id
  enable_project_monitoring = false
  container_resource_limit {
    limits_memory   = "512Mi"
    requests_memory = "256Mi"
  }
}

# Create a namespace assigned to monitoring project
resource "rancher2_namespace" "monitoring" {
  count       = var.register_in_rancher && var.enable_monitoring ? 1 : 0
  name        = "monitoring"
  project_id  = rancher2_project.monitoring[0].id
  description = "Project monitoring namespace"
  container_resource_limit {
    limits_memory   = "512Mi"
    requests_memory = "256Mi"
  }
}

# Create metrics-server app in monitoring namespace
resource "rancher2_app_v2" "metrics-server" {
  count         = var.register_in_rancher && var.enable_monitoring ? 1 : 0
  cluster_id    = rancher2_cluster_sync.this[0].cluster_id
  namespace     = "kube-system"
  name          = "metrics-server"
  repo_name     = rancher2_catalog_v2.metrics-server[0].name
  chart_name    = "metrics-server"
  chart_version = "3.8.2"
}

# Create prometheus app in monitoring namespace
resource "rancher2_app_v2" "prometheus" {
  count         = var.register_in_rancher && var.enable_monitoring ? 1 : 0
  cluster_id    = rancher2_cluster_sync.this[0].cluster_id
  namespace     = rancher2_namespace.monitoring[0].name
  name          = "kube-prometheus-stack"
  repo_name     = rancher2_catalog_v2.prometheus-community[0].name
  chart_name    = "kube-prometheus-stack"
  chart_version = "60.5.0"
  force_upgrade = "true"
  values        = <<-EOT
    cleanPrometheusOperatorObjectNames: true
#     alertmanager:
#       config:
#         global:
#           resolve_timeout: 5m
#           slack_api_url: "${var.slack_webhook_url}"
#         route:
#           group_by: ['alertname', 'namespace']
#           group_wait: 30s
#           group_interval: 40s
#           repeat_interval: 30m
#           receiver: 'null'
#           routes:
#           - receiver: 'slack'
#             matchers:
#               - alertname =~ "Watchdog|InfoInhibitor"
#         receivers:
#         - name: 'null'
#         - name: 'slack'
#           slack_configs:
#           - channel: "#prom-slack-notif"
#             title: '[{{ .Status | toUpper }}{{ if eq .Status "firing" }}:{{ .Alerts.Firing | len }}{{ end }}] Monitoring Event Notification'
#             text: >-
#               {{ range .Alerts }}
#                 *Alert:* {{ .Annotations.summary }} - `{{ .Labels.severity }}`
#                 *Details:*
#                 {{ range .Labels.SortedPairs }} • *{{ .Name }}:* `{{ .Value }}`
#                 {{ end }}
#               {{ end }}
#             send_resolved: true
#       alertmanagerSpec:
#         storage:
#           volumeClaimTemplate:
#             spec:
#               storageClassName: gp2
#               resources:
#                 requests:
#                   storage: 10Gi
    prometheus:
      prometheusSpec:
        podMonitorSelectorNilUsesHelmValues: false
        serviceMonitorSelectorNilUsesHelmValues: false
        resources:
          requests:
            memory: 3072Mi
          limits:
            memory: 6144Mi
        storageSpec:
          volumeClaimTemplate:
            spec:
              storageClassName: gp2
              resources:
               requests:
                 storage: 50Gi
        additionalScrapeConfigs:
        - job_name: kubecost
          honor_labels: true
          scrape_interval: 1m
          scrape_timeout: 10s
          metrics_path: /metrics
          scheme: http
          dns_sd_configs:
          - names:
            - kubecost-cost-analyzer.kubecost
            type: 'A'
            port: 9003
    grafana:
      adminPassword: ${var.grafana_admin_password}
      ingress:
        enabled: true
        hosts:
        - ${module.eks_cluster.cluster_name}-grafana.${var.root_domain}
        path: /*
        pathType: ImplementationSpecific
        annotations:
          kubernetes.io/ingress.class: alb
          alb.ingress.kubernetes.io/scheme: internet-facing
          alb.ingress.kubernetes.io/group.name: ${module.eks_cluster.cluster_name}
          alb.ingress.kubernetes.io/listen-ports: '[{"HTTPS":443}]'
          alb.ingress.kubernetes.io/success-codes: 200-399
          alb.ingress.kubernetes.io/healthcheck-path: /
          alb.ingress.kubernetes.io/load-balancer-attributes: idle_timeout.timeout_seconds=4000
      service:
        type: NodePort
      env:
        GF_FEATURE_TOGGLES_ENABLE: "ngalert"
      grafana.ini:
        server:
          root_url: https://${module.eks_cluster.cluster_name}-grafana.${var.root_domain}
#         auth.github:
#           enabled: ${var.github_client_id != "" && var.github_client_secret != "" ? true : false}
#           allow_sign_up: false
#           scopes: user:email,read:org
#           auth_url: https://github.com/login/oauth/authorize
#           token_url: https://github.com/login/oauth/access_token
#           api_url: https://api.github.com/user
#           allowed_organizations: "folio-org"
#           client_id: ${var.github_client_id}
#           client_secret: ${var.github_client_secret}
#           allow_assign_grafana_admin: true
#           role_attribute_path: contains(groups[*], 'folio-devops') && 'Admin' || 'Viewer'
      dashboardProviders:
        dashboardproviders.yaml:
          apiVersion: 1
          providers:
          - name: 'default'
            orgId: 1
            folder: ''
            type: file
            disableDeletion: false
            editable: true
            options:
              path: /var/lib/grafana/dashboards/default
      dashboards:
        default:
          # https://grafana.com/grafana/dashboards/9628-postgresql-database/
          postgresql-dashboard:
            gnetId: 9628
            revision: 7
            datasource:
            - name: DS_PROMETHEUS
              value: Prometheus
          # https://grafana.com/grafana/dashboards/6742-postgresql-statistics/
          postgresql-statistics-dashboard:
            gnetId: 6742
            revision: 1
            datasource:
            - name: DS_PROMETHEUS
              value: Prometheus
          # https://grafana.com/grafana/dashboards/12483-kubernetes-kafka/
          kafka-dashboard:
            gnetId: 12483
            revision: 1
            datasource:
            - name: DS_PRODUCTION-AU
              value: Prometheus
          # https://grafana.com/grafana/dashboards/10124-jvm/
          kubernetes-jvm-dashboard:
            gnetId: 10124
            revision: 1
            datasource:
            - name: datasource
              value: Prometheus
          # https://grafana.com/grafana/dashboards/14359-jvm-metrics/
          jvm-metrics-dashboard:
            gnetId: 14359
            revision: 2
            datasource:
            - name: DS_PROMETHEUS
              value: Prometheus
          # https://grafana.com/grafana/dashboards/15178-opensearch-prometheus/
          opensearch-metrics-dashboard:
            gnetId: 15178
            revision: 2
            datasource:
            - name: DS_PROMETHEUS
              value: Prometheus
      plugins:
      - grafana-piechart-panel
    prometheus-node-exporter:
      prometheus:
        monitor:
          metricRelabelings:
          - action: replace
            regex: (.*)
            replacement: $1
            sourceLabels:
            - __meta_kubernetes_pod_node_name
            targetLabel: kubernetes_node
    kubelet:
      serviceMonitor:
        resourcePath: "/metrics/resource"
        metricRelabelings:
        - action: replace
          sourceLabels:
          - node
          targetLabel: instance
  EOT
}
