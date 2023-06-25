data "aws_vpc" "this" {
  filter {
    name   = "tag:Name"
    values = [var.vpc_name]
  }
}

data "aws_subnets" "private" {
  filter {
    name   = "availability-zone"
    values = ["${var.aws_region}a"]
  }
  filter {
    name   = "vpc-id"
    values = [data.aws_vpc.this.id]
  }
  tags = {
    Type : "private"
  }
}

locals {
  broker_urls = split(",", aws_msk_cluster.this.bootstrap_brokers)
  data = jsonencode({
    KAFKA_HOST = element(split(":", local.broker_urls[0]), 0)
    KAFKA_PORT = element(split(":", local.broker_urls[0]), 1)
  })
}
