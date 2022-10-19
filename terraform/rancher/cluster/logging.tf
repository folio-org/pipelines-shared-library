#Creating a new project in Rancher.
resource "rancher2_project" "this" {
  depends_on                = [rancher2_cluster_sync.this]
  count                     = var.register_in_rancher ? 1 : 0
  provider                  = rancher2
  name                      = "Logging"
  cluster_id                = rancher2_cluster_sync.this[0].cluster_id
  enable_project_monitoring = false
  container_resource_limit {
    limits_memory   = "512Mi"
    requests_cpu    = "80m"
    requests_memory = "400Mi"
  }
}

# Create a new rancher2 Namespace assigned to cluster project
resource "rancher2_namespace" "this" {
  depends_on  = [rancher2_cluster_sync.this]
  count       = var.register_in_rancher ? 1 : 0
  name        = "logging"
  project_id  = rancher2_project.this[0].id
  description = "Project logging namespace"
  container_resource_limit {
    limits_memory   = "512Mi"
    requests_cpu    = "80m"
    requests_memory = "400Mi"
  }
}

# Create rancher2 Elasticsearch app in logging namespace
resource "rancher2_app_v2" "elasticsearch" {
  depends_on    = [rancher2_catalog_v2.bitnami]
  count         = var.register_in_rancher ? 1 : 0
  cluster_id    = rancher2_cluster_sync.this[0].cluster_id
  namespace     = rancher2_namespace.this[0].name
  name          = "elasticsearch"
  repo_name     = "bitnami"
  chart_name    = "elasticsearch"
  chart_version = "17.9.29" #"19.1.4"
  force_upgrade = "true"
  values        = <<-EOT
    global:
      kibanaEnabled: true
    data:
      persistence:
        size: 128Gi
    data:
      resources:
        limits:
          memory: 3096Mi
    kibana:
      resources:
        requests:
          memory: 1024Mi
        limits:
          memory: 2048Mi
      service:
        type: NodePort
      ingress:
        enabled: true
        hostname: "${module.eks_cluster.cluster_id}-kibana.${var.root_domain}"
        path: "/*"
        annotations:
          kubernetes.io/ingress.class: alb
          alb.ingress.kubernetes.io/scheme: internet-facing
          alb.ingress.kubernetes.io/group.name: ${module.eks_cluster.cluster_id}
          alb.ingress.kubernetes.io/listen-ports: '[{"HTTPS":443}]'
          alb.ingress.kubernetes.io/success-codes: 200-399
          alb.ingress.kubernetes.io/healthcheck-path: /
          alb.ingress.kubernetes.io/load-balancer-attributes: idle_timeout.timeout_seconds=4000
    master:
      service:
        type: NodePort
      ingress:
        enabled: true
        hostname: "${module.eks_cluster.cluster_id}-elasticsearch.${var.root_domain}"
        path: "/*"
        annotations:
          kubernetes.io/ingress.class: alb
          alb.ingress.kubernetes.io/scheme: internet-facing
          alb.ingress.kubernetes.io/group.name: ${module.eks_cluster.cluster_id}
          alb.ingress.kubernetes.io/listen-ports: '[{"HTTPS":443}]'
          alb.ingress.kubernetes.io/success-codes: 200-399
          alb.ingress.kubernetes.io/healthcheck-path: /
          alb.ingress.kubernetes.io/load-balancer-attributes: idle_timeout.timeout_seconds=4000
  EOT
}

# Create Elasticsearch manifest
resource "kubectl_manifest" "elasticsearch_output" {
  depends_on         = [rancher2_app_v2.elasticsearch]
  count              = var.register_in_rancher ? 1 : 0
  provider           = kubectl
  override_namespace = rancher2_namespace.this[0].name
  yaml_body          = <<YAML
apiVersion: v1
kind: ConfigMap
metadata:
  name: elasticsearch-output
data:
  fluentd.conf: |

    # Ignore fluentd own events
    <match fluent.**>
      @type null
    </match>

    # TCP input to receive logs from the forwarders
    <source>
      @type forward
      bind 0.0.0.0
      port 24224
    </source>

    # HTTP input for the liveness and readiness probes
    <source>
      @type http
      bind 0.0.0.0
      port 9880
    </source>

    # Throw the healthcheck to the standard output instead of forwarding it
    <match fluentd.healthcheck>
      @type stdout
    </match>

    # Send the logs to the standard output
    <match **>
      @type elasticsearch_dynamic
      include_tag_key true
      host "#{ENV['ELASTICSEARCH_HOST']}"
      port "#{ENV['ELASTICSEARCH_PORT']}"
      scheme http
      logstash_format true
      logstash_prefix logstash-${record["kubernetes"]["namespace_name"]}
      <buffer>
        @type file
        path /opt/bitnami/fluentd/logs/buffers/logs.buffer
        flush_thread_count 2
        flush_interval 5s
      </buffer>
    </match>
  YAML
}

# Create rancher2 Elasticsearch app in logging namespace
resource "rancher2_app_v2" "fluentd" {
  depends_on    = [kubectl_manifest.elasticsearch_output]
  count         = var.register_in_rancher ? 1 : 0
  cluster_id    = rancher2_cluster_sync.this[0].cluster_id
  namespace     = rancher2_namespace.this[0].name
  name          = "fluentd"
  repo_name     = "bitnami"
  chart_name    = "fluentd"
  chart_version = "5.3.0"
  force_upgrade = "true"
  values        = <<-EOT
    image:
      tag: 1.14.1-debian-10-r28
    aggregator:
      configMap: elasticsearch-output
      extraEnvVars:
        - name: ELASTICSEARCH_HOST
          value: "elasticsearch-master"
        - name: ELASTICSEARCH_PORT
          value: "9200"
  EOT
}

// Create an index lifecycle policy 
resource "elasticstack_elasticsearch_index_lifecycle" "logstash_policy" {
  name = var.index_policy_name

  hot {
    min_age = "0ms"
    set_priority {
      priority = 100
    }
  }

  delete {
    min_age = "31d"
    delete {}
  }

}

// Create an index template for the policy
resource "elasticstack_elasticsearch_index_template" "logstash_template" {
  name = var.index_template_name
  index_patterns = ["logstash*"]

  template {
    settings = jsonencode({
      "lifecycle.name" = elasticstack_elasticsearch_index_lifecycle.logstash_policy.name
    })
  }
}
