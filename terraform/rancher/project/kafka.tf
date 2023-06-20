# Rancher2 Project App Kafka
resource "rancher2_app_v2" "kafka" {
  count         = var.kafka_shared ? 0 : 1
  cluster_id    = data.rancher2_cluster.this.id
  namespace     = rancher2_namespace.this.name
  name          = "kafka-${var.rancher_project_name}"
  repo_name     = "bitnami"
  chart_name    = "kafka"
  chart_version = "17.2.3"
  force_upgrade = "true"
  values        = <<-EOT
    image:
      tag: 2.8.1-debian-10-r99
    metrics:
      kafka:
        enabled: true
      jmx:
        enabled: true
      serviceMonitor:
        enabled: true
        namespace: monitoring
        interval: 30s
        scrapeTimeout: 30s
    persistence:
      enabled: true
      size: ${join("", [var.kafka_ebs_volume_size, "Gi"])}
      storageClass: gp2
    resources:
      requests:
        memory: 2048Mi
      limits:
        memory: 4096Mi
    zookeeper:
      image:
        tag: 3.7.0-debian-10-r257
      enabled: true
      persistence:
        size: 10Gi
      resources:
        requests:
          memory: 512Mi
        limits:
          memory: 768Mi
    livenessProbe:
      enabled: false
    readinessProbe:
      enabled: false
    replicaCount: ${var.kafka_number_of_broker_nodes}
    heapOpts: "-Xmx3277m -Xms1024m"
    extraEnvVars:
      - name: KAFKA_DELETE_TOPIC_ENABLE
        value: "true"
  EOT
}

resource "rancher2_app_v2" "kafka_ui" {
  count         = var.kafka_shared ? 0 : 1
  cluster_id    = data.rancher2_cluster.this.id
  namespace     = rancher2_namespace.this.name
  name          = "kafka-ui"
  repo_name     = "provectus"
  chart_name    = "kafka-ui"
  chart_version = "0.7.1"
  force_upgrade = "true"
  values        = <<-EOT
    service:
      type: NodePort
    ingress:
      host: ${join(".", [join("-", [data.rancher2_cluster.this.name, var.rancher_project_name, "kafka-ui"]), var.root_domain])}
      path: "/"
      enabled: true
      annotations:
        kubernetes.io/ingress.class: alb
        alb.ingress.kubernetes.io/scheme: internet-facing
        alb.ingress.kubernetes.io/group.name: ${local.group_name}
        alb.ingress.kubernetes.io/listen-ports: '[{"HTTPS":443}]'
        alb.ingress.kubernetes.io/success-codes: 200-399
    yamlApplicationConfig:
      kafka:
        clusters:
          - name: ${join("-", [data.rancher2_cluster.this.name, var.rancher_project_name])}
            bootstrapServers: "kafka-${var.rancher_project_name}:9092"
      auth:
        type: disabled
      management:
        health:
          ldap:
            enabled: false
  EOT
}
