# Rancher2 Project App Kafka
resource "rancher2_app_v2" "kafka" {
  count         = var.kafka_embedded ? 1 : 0
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
      size: ${var.kafka_ebs_volume_size}
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
resource "aws_security_group" "kafka" {
  count       = var.kafka_embedded ? 0 : 1
  name        = "allow-kafka-${local.env_name}"
  description = "Allow connection to Kafka"
  vpc_id      = data.aws_eks_cluster.this.vpc_config[0].vpc_id

  ingress {
    description = "Allow 9092 port"
    from_port   = 9092
    to_port     = 9092
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = merge(
    var.tags,
    {
      Name = "allow-kafka"
  })
}
resource "aws_msk_configuration" "this" {
  count             = var.kafka_embedded ? 0 : 1
  kafka_versions    = [var.kafka_version]
  name              = "kafka-configuration-${local.env_name}"
  description       = "Kafka configuration which is used for rancher env"
  server_properties = <<PROPERTIES
auto.create.topics.enable = true
PROPERTIES
}
resource "aws_msk_cluster" "this" {
  count                  = var.kafka_embedded ? 0 : 1
  cluster_name           = "kafka-${local.env_name}"
  kafka_version          = var.kafka_version
  number_of_broker_nodes = var.kafka_number_of_broker_nodes

  encryption_info {
    encryption_in_transit {
      client_broker = "PLAINTEXT"
    }
  }

  broker_node_group_info {
    instance_type = var.kafka_instance_type
    #    client_subnets  = data.aws_subnets.private.ids
    client_subnets  = slice(data.aws_subnets.private.ids, 0, var.kafka_number_of_broker_nodes)
    security_groups = [aws_security_group.kafka[count.index].id]
    storage_info {
      ebs_storage_info {
        volume_size = var.kafka_ebs_volume_size
      }
    }
  }

  configuration_info {
    arn      = aws_msk_configuration.this[count.index].arn
    revision = aws_msk_configuration.this[count.index].latest_revision
  }

  tags = merge(
    var.tags,
    {
      service               = "Kafka"
      name                  = "kafka-${local.env_name}"
      version               = var.kafka_version
      kubernetes_cluster    = data.rancher2_cluster.this.name
      kubernetes_namespace  = var.rancher_project_name
      kubernetes_label_team = var.rancher_project_name
      team                  = var.rancher_project_name
      kubernetes_service    = "MSK-Cluster"
      kubernetes_controller = "MSK-${local.env_name}"
  })
}
resource "rancher2_app_v2" "kafka_ui" {
  count         = var.kafka_ui ? 1 : 0
  cluster_id    = data.rancher2_cluster.this.id
  namespace     = rancher2_namespace.this.name
  name          = "kafka-ui"
  repo_name     = "provectus"
  chart_name    = "kafka-ui"
  chart_version = "0.4.3"
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
            bootstrapServers: ${var.kafka_embedded ? "kafka-${var.rancher_project_name}" : element(split(":", aws_msk_cluster.this[0].bootstrap_brokers), 0)}:9092
      auth:
        type: disabled
      management:
        health:
          ldap:
            enabled: false
  EOT
}
