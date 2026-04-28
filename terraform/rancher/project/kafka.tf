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
  version    = "31.2.0"
  values = [<<-EOF
global:
  security:
    allowInsecureImages: true
image:
  tag: 4.2.0-debian-12-r2
  registry: 732722833398.dkr.ecr.us-west-2.amazonaws.com
  repository: kafka
  pullPolicy: IfNotPresent
clusterId: "MkU3OEVBNTcwNTJENDM2Qk"
listeners:
  client:
    name: PLAINTEXT
    protocol: PLAINTEXT
    containerPort: 9092
  controller:
    name: CONTROLLER
    protocol: PLAINTEXT
  interbroker:
    name: INTERBROKER
    protocol: PLAINTEXT
controller:
  replicaCount: ${var.kafka_number_of_broker_nodes}
  controllerOnly: false
  heapOpts: "-XX:MaxRAMPercentage=75.0"
  resources:
    requests:
      memory: 2Gi
    limits:
      memory: '${var.kafka_max_mem_size}Mi'
  persistence:
    enabled: true
    size: ${join("", [var.kafka_ebs_volume_size, "Gi"])}
    storageClass: gp2
  livenessProbe:
    enabled: false
  readinessProbe:
    enabled: false
  advertisedListeners: "PLAINTEXT://kafka-${var.rancher_project_name}:9092"
  extraEnvVars:
    - name: KAFKA_CFG_DELETE_TOPIC_ENABLE
      value: "true"
  ${indent(2, local.schedule_value)}
broker:
  replicaCount: 0
metrics:
  jmx:
    image:
      tag: 1.5.0-debian-12-r8
      registry: 732722833398.dkr.ecr.us-west-2.amazonaws.com
      repository: jmx-exporter
      pullPolicy: IfNotPresent
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
image:
  tag: v0.7.2
  registry: 732722833398.dkr.ecr.us-west-2.amazonaws.com
  repository: kafka-ui
  pullPolicy: IfNotPresent
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
