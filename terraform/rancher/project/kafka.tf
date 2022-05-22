# Rancher2 Project App Kafka
resource "rancher2_app" "kafka" {
  count            = var.folio_embedded_kafka ? 1 : 0
  project_id       = rancher2_project.project.id
  target_namespace = rancher2_namespace.project-namespace.name
  catalog_name     = "bitnami"
  name             = "kafka"
  template_name    = "kafka"
  force_upgrade    = "true"
  answers = {
    "image.tag"                  = var.kafka_version
    "global.storageClass"        = "gp2"
    "metrics.kafka.enabled"      = "false"
    "persistence.enabled"        = "true"
    "persistence.size"           = "10Gi"
    "persistence.storageClass"   = "gp2"
    "resources.limits.cpu"       = "500m"
    "resources.limits.memory"    = "1200Mi"
    "resources.requests.cpu"     = "250m"
    "resources.requests.memory"  = "1100Mi" // originally 256Mi
    "zookeeper.enabled"          = "true"
    "zookeeper.persistence.size" = "5Gi"
    "livenessProbe.enabled"      = "false"
    "readinessProbe.enabled"     = "false"
  }
}

#resource "aws_security_group" "kafka_sg" {
#  name        = "kafka_sg"
#  description = "Allow connection to Kafka"
#  vpc_id      = module.vpc.vpc_id
#
#  ingress {
#    description      = "Allow 9092 port"
#    from_port        = 9092
#    to_port          = 9092
#    protocol         = "tcp"
#    cidr_blocks      = ["0.0.0.0/0"]
#
#  }
#
#  egress {
#    from_port        = 0
#    to_port          = 0
#    protocol         = "-1"
#    cidr_blocks      = ["0.0.0.0/0"]
#  }
#
#  tags = {
#    Name = "allow connection to Kafka"
#  }
#}
#resource "aws_msk_cluster" "kafka" {
#  depends_on             = [module.rds]
#  cluster_name           = "Kafka-${var.name_prefix}"
#  kafka_version          = "2.8.0"
#  number_of_broker_nodes = 2
#  encryption_info {
#    encryption_in_transit {
#      client_broker = "PLAINTEXT"
#    }
#  }
#
#  broker_node_group_info {
#    instance_type   = "kafka.m5.large"
#    ebs_volume_size = 100
#    client_subnets = [
#      module.vpc.private_subnets[0],
#      module.vpc.private_subnets[1],
#    ]
#    security_groups = [aws_security_group.kafka_sg.id]
#  }
#
#  configuration_info {
#    arn      = aws_msk_configuration.kafka_configuration.arn
#    revision = aws_msk_configuration.kafka_configuration.latest_revision
#  }
#
#  tags = {
#    service = "Kafka"
#    name = "KAFKA-PERF-${var.name_prefix}"
#  }
#}
#resource "aws_msk_configuration" "kafka_configuration" {
#  kafka_versions    = ["2.6.1"]
#  name              = "Kafka-configuration-${var.name_prefix}"
#  description       = "Kafka configuration which is used for perf rancher env"
#  server_properties = <<PROPERTIES
#auto.create.topics.enable = true
#PROPERTIES
#}
