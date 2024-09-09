resource "rancher2_secret" "kafka-credentials" {
  name         = "kafka-credentials"
  project_id   = rancher2_project.this.id
  namespace_id = rancher2_namespace.this.id
  data = {
    ENV        = base64encode(local.env_name)
    KAFKA_HOST = base64encode(var.kafka_shared ? local.msk_value["KAFKA_HOST"] : "kafka-${var.rancher_project_name}")
    KAFKA_PORT = base64encode("9092")
  }
}

# Kafka deployment
resource "helm_release" "kafka" {
  count      = var.kafka_shared ? 0 : 1
  namespace  = rancher2_namespace.this.name
  repository = local.catalogs.bitnami
  name       = "kafka-${var.rancher_project_name}"
  chart      = "kafka"
  version    = "21.4.6"
  values = [<<-EOF
image:
  tag: 3.5
config: |-
  listeners=PLAINTEXT://:9092
  advertised.listeners=PLAINTEXT://kafka-${rancher2_namespace.this.name}-zookeeper:9092
  heartbeat.interval.ms=600000
  max.poll.interval.ms=600000
  session.timeout.ms=600000
  zookeeper.connect=kafka-${rancher2_namespace.this.name}-zookeeper
  zookeeper.connection.timeout.ms=6000
metrics:
  kafka:
    enabled: true
    resources:
      limits:
        memory: 1280Mi
      requests:
        memory: 256Mi
    ${indent(4, local.schedule_value)}
  jmx:
    enabled: true
    resources:
      limits:
        memory: 2048Mi
      requests:
        memory: 1024Mi
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
    memory: 2Gi
  limits:
    memory: '${var.kafka_max_mem_size}Mi'
zookeeper:
  image:
    tag: 3.7
  enabled: true
  persistence:
    size: 5Gi
  resources:
    requests:
      memory: 512Mi
    limits:
      memory: 768Mi
  ${indent(2, local.schedule_value)}
livenessProbe:
  enabled: false
readinessProbe:
  enabled: false
replicaCount: ${var.kafka_number_of_broker_nodes}
heapOpts: "-XX:MaxRAMPercentage=75.0"
extraEnvVars:
  - name: KAFKA_DELETE_TOPIC_ENABLE
    value: "true"
${local.schedule_value}
EOF
  ]
}

# Kafka UI deployment
resource "helm_release" "kafka-ui" {
  count      = var.kafka_shared ? 0 : 1
  namespace  = rancher2_namespace.this.name
  repository = local.catalogs.provectus
  name       = "kafka-ui"
  chart      = "kafka-ui"
  version    = "0.7.1"
  values = [<<-EOF
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
resources:
  requests:
    memory: 512Mi
  limits:
    memory: 768Mi
yamlApplicationConfig:
  kafka:
    clusters:
      - name: ${join("-", [data.rancher2_cluster.this.name, var.rancher_project_name])}
        bootstrapServers: "${helm_release.kafka[0].name}:9092"
  auth:
    type: disabled
  management:
    health:
      ldap:
        enabled: false
  resources:
    requests:
      memory: 512Mi
    limits:
      memory: 3Gi
${local.schedule_value}
EOF
  ]
}
