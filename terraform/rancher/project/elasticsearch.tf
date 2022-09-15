# Create a new rancher2 Folio OpenSearch App in a default Project namespace
resource "rancher2_app_v2" "opensearch" {
  count         = var.es_embedded ? 1 : 0
  cluster_id    = data.rancher2_cluster.this.id
  namespace     = rancher2_namespace.this.name
  name          = "opensearch"
  repo_name     = "opensearch"
  chart_name    = "opensearch"
  chart_version = "1.14.0"
  force_upgrade = "true"
  values        = <<-EOT
    masterService: "opensearch-${var.rancher_project_name}""
    replicas: 2
    extraEnvs:
      - name: DISABLE_SECURITY_PLUGIN
        value: "true"
    resources:
      requests:
        cpu: "1000m"
        memory: "2048Mi"
      limits:
        cpu: "1500m"
        memory: "4096Mi"
    plugins:
      enabled: true
      installList: [analysis-icu, analysis-kuromoji, analysis-smartcn, analysis-nori, analysis-phonetic]
  EOT
}

# Create a new rancher2 Folio ElasticSearch App in a default Project namespace
/*resource "rancher2_app_v2" "elasticsearch" {
  count         = var.es_embedded ? 1 : 0
  cluster_id    = data.rancher2_cluster.this.id
  namespace     = rancher2_namespace.this.name
  name          = "elasticsearch"
  repo_name     = "bitnami"
  chart_name    = "elasticsearch"
  chart_version = "17.9.29"
  force_upgrade = "true"
  values        = <<-EOT
    image:
      debug: true
    global:
      coordinating:
        name: ${var.rancher_project_name}
    coordinating:
      replicas: 1
      resources:
        requests:
          cpu: 256m
          memory: 1024Mi
        limits:
          cpu: 512m
          memory: 2048Mi
      livenessProbe:
        initialDelaySeconds: 360
    data:
      replicas: 1
      resources:
        requests:
          cpu: 256m
          memory: 1024Mi
        limits:
          cpu: 512m
          memory: 2048Mi
      livenessProbe:
        initialDelaySeconds: 360
    master:
      replicas: 1
      resources:
        requests:
          cpu: 256m
          memory: 1024Mi
        limits:
          cpu: 512m
          memory: 2048Mi
      livenessProbe:
        initialDelaySeconds: 360
    plugins: "analysis-icu, analysis-kuromoji, analysis-smartcn, analysis-nori, analysis-phonetic"
  EOT
}*/

resource "random_password" "es_password" {
  count       = var.es_embedded ? 0 : 1
  length      = 16
  special     = true
  numeric     = true
  upper       = true
  lower       = true
  min_lower   = 1
  min_numeric = 1
  min_special = 1
  min_upper   = 1
}

resource "aws_security_group" "es" {
  count       = var.es_embedded ? 0 : 1
  name        = "allow-es-${local.env_name}"
  description = "Allow connection to Elasticsearch"
  vpc_id      = data.aws_eks_cluster.this.vpc_config[0].vpc_id

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

module "aws_es" {
  count   = var.es_embedded ? 0 : 1
  source  = "lgallard/elasticsearch/aws"
  version = "0.13.0"

  elasticsearch_version                          = var.es_version
  domain_name                                    = "es-${local.env_name}"
  domain_endpoint_options_enforce_https          = true
  create_service_link_role                       = var.es_create_service_link_role
  cognito_options_enabled                        = false
  node_to_node_encryption_enabled                = true
  snapshot_options_automated_snapshot_start_hour = 23
  timeouts_update                                = "60m"

  advanced_options = {
    "rest.action.multi.allow_explicit_index" = "true"
  }

  cluster_config = {
    dedicated_master_enabled = var.es_dedicated_master
    instance_count           = var.es_instance_count
    instance_type            = var.es_instance_type
    zone_awareness_enabled   = "false"
    availability_zone_count  = 2
    dedicated_master_count   = 0
  }

  advanced_security_options = {
    enabled                        = true
    internal_user_database_enabled = true
    master_user_options = {
      master_user_name     = var.es_username
      master_user_password = random_password.es_password[count.index].result
    }
  }

  ebs_options = {
    ebs_enabled = var.es_ebs_volume_size > 0 ? "true" : "false"
    volume_size = var.es_ebs_volume_size
  }

  vpc_options = {
    subnet_ids         = data.aws_subnets.private.ids
    security_group_ids = tolist([aws_security_group.es[count.index].id])
  }

  access_policies = templatefile("${path.module}/resources/es-access-policies.tpl", {
    region      = var.aws_region,
    account     = data.aws_caller_identity.current.account_id,
    domain_name = "es-${local.env_name}"
  })

  tags = merge(
    var.tags,
    {
      service = "ElasticSearch"
      name    = "es-${local.env_name}"
      version = var.es_version
  })
}
