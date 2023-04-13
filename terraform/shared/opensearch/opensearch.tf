resource "aws_ssm_parameter" "opensearch" {
  name  = var.service_name
  type  = "String"
  value = local.connection_config

  tags = merge(
    var.tags,
    {
      Name = "parameter-storage"
    }
  )
}

resource "random_password" "os_password" {
  length           = 16
  special          = true
  numeric          = true
  upper            = true
  lower            = true
  min_lower        = 1
  min_numeric      = 1
  min_special      = 1
  min_upper        = 1
  override_special = "@$%-+=?"
}

resource "aws_security_group" "opensearch" {
  name        = "allow-es-${var.service_name}"
  description = "Allow connection to Elasticsearch"
  vpc_id      = data.aws_vpc.this.id

  ingress {
    from_port   = 443
    to_port     = 443
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
      Name = "allow-es"
  })
}

module "aws_opensearch" {
  source  = "lgallard/elasticsearch/aws"
  version = "0.14.0"

  elasticsearch_version                          = var.os_version
  domain_name                                    = var.service_name
  domain_endpoint_options_enforce_https          = true
  create_service_link_role                       = var.os_create_service_link_role
  cognito_options_enabled                        = false
  node_to_node_encryption_enabled                = true
  snapshot_options_automated_snapshot_start_hour = 23
  timeouts_update                                = "60m"

  advanced_options = {
    "rest.action.multi.allow_explicit_index" = "true"
  }

  cluster_config = {
    dedicated_master_enabled = var.os_dedicated_master
    instance_count           = var.os_instance_count
    instance_type            = var.os_instance_type
    zone_awareness_enabled   = "true"
    availability_zone_count  = var.os_instance_count
    dedicated_master_count   = 0
  }

  advanced_security_options = {
    enabled                        = true
    internal_user_database_enabled = true
    master_user_options = {
      master_user_name     = var.os_username
      master_user_password = local.os_password
    }
  }

  ebs_options = {
    ebs_enabled = var.os_ebs_volume_size > 0 ? "true" : "false"
    volume_size = var.os_ebs_volume_size
  }

  vpc_options = {
    subnet_ids         = data.aws_subnets.private.ids
    security_group_ids = tolist([aws_security_group.opensearch.id])
  }

  access_policies = templatefile("${path.module}/resources/es-access-policies.tpl", {
    region      = var.aws_region,
    account     = data.aws_caller_identity.current.account_id,
    domain_name = var.service_name
  })

  tags = merge(
    var.tags,
    {
      service            = "OpenSearch"
      name               = var.service_name
      version            = var.os_version
      kubernetes_service = "Folio-OpenSearch-Cluster"
  })
}
