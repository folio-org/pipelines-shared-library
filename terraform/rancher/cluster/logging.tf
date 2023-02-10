#Creating a new project in Rancher.
resource "rancher2_project" "logging" {
  depends_on                = [time_sleep.wait_300_seconds]
  count                     = var.register_in_rancher ? 1 : 0
  provider                  = rancher2
  name                      = "logging"
  cluster_id                = rancher2_cluster_sync.this[0].cluster_id
  enable_project_monitoring = false
  container_resource_limit {
    limits_memory   = "1024Mi"
    requests_memory = "256Mi"
  }
}

# Create a new rancher2 Namespace assigned to cluster project
resource "rancher2_namespace" "logging" {
  count       = var.register_in_rancher ? 1 : 0
  name        = "logging"
  project_id  = rancher2_project.logging[0].id
  description = "Project logging namespace"
  container_resource_limit {
    limits_memory   = "1024Mi"
    requests_memory = "256Mi"
  }
}

# Create rancher2 Elasticsearch app in logging namespace
resource "rancher2_app_v2" "elasticsearch" {
  depends_on    = [rancher2_catalog_v2.bitnami]
  count         = var.register_in_rancher ? 1 : 0
  cluster_id    = rancher2_cluster_sync.this[0].cluster_id
  namespace     = rancher2_namespace.logging[0].name
  name          = "elasticsearch"
  repo_name     = "bitnami"
  chart_name    = "elasticsearch"
  chart_version = "17.9.29" #"19.1.4"
  values        = <<-EOT
    global:
      kibanaEnabled: true
    extraConfig:
      http:
        max_content_length: 200mb
    master:
      heapSize: 2560m
      replicas: 2
      resources:
        requests:
          memory: 512Mi
        limits:
          memory: 3096Mi
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
    data:
      heapSize: 1536m
      persistence:
        size: 100Gi
      resources:
        requests:
          memory: 1536Mi
        limits:
          memory: 2560Mi
    kibana:
      resources:
        requests:
          memory: 768Mi
        limits:
          memory: 1024Mi
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
  EOT
}

# Create Elasticsearch manifest
resource "kubectl_manifest" "elasticsearch_output" {
  depends_on         = [rancher2_app_v2.elasticsearch]
  count              = var.register_in_rancher ? 1 : 0
  provider           = kubectl
  override_namespace = rancher2_namespace.logging[0].name
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
      suppress_type_name true
      reconnect_on_error true
      reload_on_failure true
      reload_connections false
      request_timeout 120s
      logstash_prefix logstash-$${record["kubernetes"]["namespace_name"]}
      <buffer>
        @type file
        retry_forever false
        retry_max_times 3
        retry_wait 10
        retry_max_interval 300
        path /opt/bitnami/fluentd/logs/buffers/logs.buffer
        flush_thread_count 4
        flush_interval 15s
        chunk_limit_size 80M
      </buffer>
    </match>
  YAML
}

# Create rancher2 Elasticsearch app in logging namespace
resource "rancher2_app_v2" "fluentd" {
  depends_on    = [rancher2_app_v2.elasticsearch, kubectl_manifest.elasticsearch_output]
  count         = var.register_in_rancher ? 1 : 0
  cluster_id    = rancher2_cluster_sync.this[0].cluster_id
  namespace     = rancher2_namespace.logging[0].name
  name          = "fluentd"
  repo_name     = "bitnami"
  chart_name    = "fluentd"
  chart_version = "5.3.0"
  values        = <<-EOT
    image:
      tag: 1.14.1-debian-10-r28
    forwarder:
      fluentd-output.conf: |
        # Throw the healthcheck to the standard output instead of forwarding it
        <match fluentd.healthcheck>
          @type stdout
        </match>
        {{- if .Values.aggregator.enabled }}
        # Forward all logs to the aggregators
        <match **>
          @type forward
          {{- if .Values.tls.enabled }}
          transport tls
          tls_cert_path /opt/bitnami/fluentd/certs/out_forward/ca.crt
          tls_client_cert_path /opt/bitnami/fluentd/certs/out_forward/tls.crt
          tls_client_private_key_path /opt/bitnami/fluentd/certs/out_forward/tls.key
          {{- end }}
          {{- $fullName := (include "common.names.fullname" .) }}
          {{- $global := . }}
          {{- $domain := default "cluster.local" .Values.clusterDomain }}
          {{- $port := .Values.aggregator.port | int }}
          {{- range $i, $e := until (.Values.aggregator.replicaCount | int) }}
          <server>
            {{ printf "host %s-%d.%s-headless.%s.svc.%s" $fullName $i $fullName $global.Release.Namespace $domain }}
            {{ printf "port %d" $port }}
            {{- if ne $i 0 }}
            standby
            {{- end }}
          </server>
          {{- end }}
          <buffer>
            @type file
            path /opt/bitnami/fluentd/logs/buffers/logs.buffer
            flush_thread_count 2
            flush_interval 5s
            chunk_limit_size 80M
          </buffer>
        </match>
        {{- else }}
        # Send the logs to the standard output
        <match **>
          @type stdout
        </match>
        {{- end }}
    aggregator:
      resources:
        requests:
          memory: 512Mi
        limits:
          memory: 2048Mi
      configMap: elasticsearch-output
      extraEnvVars:
        - name: ELASTICSEARCH_HOST
          value: "elasticsearch-master"
        - name: ELASTICSEARCH_PORT
          value: "9200"
  EOT
}

// Create an index lifecycle policy
resource "elasticstack_elasticsearch_index_lifecycle" "index_policy" {
  depends_on = [rancher2_app_v2.elasticsearch]
  count      = var.register_in_rancher ? 1 : 0
  name       = var.index_policy_name

  hot {
    min_age = "0ms"
    set_priority {
      priority = 100
    }
  }

  delete {
    min_age = "7d"
    delete {}
  }

}

// Create an index template for the policy
resource "elasticstack_elasticsearch_index_template" "index_template" {
  count          = var.register_in_rancher ? 1 : 0
  name           = var.index_template_name
  index_patterns = ["logstash*"]

  template {
    settings = jsonencode({
      "lifecycle.name" = elasticstack_elasticsearch_index_lifecycle.index_policy[0].name
    })
  }
}
