# Rancher2 Project App Kafka
resource "rancher2_app_v2" "kafka" {
  count         = var.kafka_embedded ? 1 : 0
  cluster_id    = data.rancher2_cluster.this.id
  namespace     = rancher2_namespace.this.name
  name          = "kafka"
  repo_name     = "bitnami"
  chart_name    = "kafka"
  chart_version = "14.9.3"
  force_upgrade = "true"
  replica_count = var.kafka_number_of_broker_nodes
  values        = <<-EOT
    metrics:
      kafka:
        enabled: false
    persistence:
      enabled: true
      size: ${var.kafka_ebs_volume_size}
      storageClass: gp2
    resources:
      requests:
        memory: 1100Mi
      limits:
        memory: 4096Mi
    zookeeper:
      enabled: true
      persistence:
        size: 5Gi
    livenessProbe:
      enabled: false
    readinessProbe:
      enabled: false
    heapOpts: "-Xmx2662m -Xms1024m"
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
    instance_type   = var.kafka_instance_type
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
      service = "Kafka"
      name    = "kafka-${local.env_name}"
      version = var.kafka_version
  })
}
