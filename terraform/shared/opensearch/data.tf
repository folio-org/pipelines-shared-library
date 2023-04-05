data "aws_vpc" "this" {
  filter {
    name   = "tag:Name"
    values = [var.vpc_name]
  }
}

data "aws_subnets" "private" {
  filter {
    name   = "availability-zone"
    values = ["${var.aws_region}a", "${var.aws_region}b"]
  }
  filter {
    name   = "vpc-id"
    values = [data.aws_vpc.this.id]
  }
  tags = {
    Type : "private"
  }
}

# Used for accessing Account ID and ARN
data "aws_caller_identity" "current" {}

# Creating local variables that are used in the rest of the terraform file.
locals {
  connection_config        = jsonencode({
    ELASTICSEARCH_URL      = base64encode("https://${module.aws_opensearch.endpoint}:443")
    ELASTICSEARCH_HOST     = base64encode(module.aws_opensearch.endpoint)
    ELASTICSEARCH_PORT     = base64encode("443")
    ELASTICSEARCH_USERNAME = base64encode(var.os_username)
    ELASTICSEARCH_PASSWORD = base64encode(random_password.os_password.result)
  })
}
