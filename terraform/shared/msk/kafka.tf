resource "aws_security_group" "kafka" {
  name        = "allow-${var.service_name}"
  description = "Allow connection to Kafka"
  vpc_id      = data.aws_vpc.this.id

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
  kafka_versions    = [var.kafka_version]
  name              = "${var.service_name}-configuration"
  description       = "Shared Kafka configuration which is used for rancher envs"
  server_properties = <<PROPERTIES
auto.create.topics.enable = true
PROPERTIES
}

resource "aws_msk_cluster" "this" {
  cluster_name           = var.service_name
  kafka_version          = var.kafka_version
  number_of_broker_nodes = var.kafka_number_of_broker_nodes

  encryption_info {
    encryption_in_transit {
      client_broker = "PLAINTEXT"
    }
  }

  broker_node_group_info {
    instance_type   = var.kafka_instance_type
    client_subnets  = data.aws_subnets.private.ids
    security_groups = [aws_security_group.kafka.id]
    storage_info {
      ebs_storage_info {
        volume_size = var.kafka_ebs_volume_size
      }
    }
  }

  configuration_info {
    arn      = aws_msk_configuration.this.arn
    revision = aws_msk_configuration.this.latest_revision
  }

  tags = merge(
    var.tags,
    {
      service            = "Kafka"
      name               = var.service_name
      version            = var.kafka_version
      kubernetes_service = "Folio-MSK-Cluster"
  })
}

resource "aws_ssm_parameter" "kafka" {
  name  = var.service_name
  type  = "String"
  value = local.data

  tags = merge(
    var.tags,
    {
      Name = "${var.service_name}-parameters"
    }
  )
}