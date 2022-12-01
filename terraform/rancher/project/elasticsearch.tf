# Create a new rancher2 Folio OpenSearch App in a default Project namespace
resource "rancher2_app_v2" "opensearch-master" {
  count         = var.es_embedded ? 1 : 0
  cluster_id    = data.rancher2_cluster.this.id
  namespace     = rancher2_namespace.this.name
  name          = "opensearch-master"
  repo_name     = "opensearch"
  chart_name    = "opensearch"
  chart_version = "1.14.0"
  force_upgrade = "true"
  values        = <<-EOT
    clusterName: "opensearch-${var.rancher_project_name}"
    masterService: "opensearch-${var.rancher_project_name}"
    nodeGroup: "master"
    replicas: 1
    roles:
      - master
    extraEnvs:
      - name: DISABLE_SECURITY_PLUGIN
        value: "true"
    resources:
      requests:
        memory: 1536Mi
      limits:
        memory: 2048Mi
    plugins:
      enabled: true
      installList: [analysis-icu, analysis-kuromoji, analysis-smartcn, analysis-nori, analysis-phonetic]
  EOT
}

resource "rancher2_app_v2" "opensearch-data" {
  #depends_on    = [rancher2_app_v2.opensearch-master]
  count         = var.es_embedded ? 1 : 0
  cluster_id    = data.rancher2_cluster.this.id
  namespace     = rancher2_namespace.this.name
  name          = "opensearch-data"
  repo_name     = "opensearch"
  chart_name    = "opensearch"
  chart_version = "1.14.0"
  force_upgrade = "true"
  values        = <<-EOT
    clusterName: "opensearch-${var.rancher_project_name}"
    masterService: "opensearch-${var.rancher_project_name}"
    nodeGroup: "data"
    replicas: 1
    roles:
      - data
    extraEnvs:
      - name: DISABLE_SECURITY_PLUGIN
        value: "true"
    resources:
      requests:
        memory: 1536Mi
      limits:
        memory: 2048Mi
    persistence:
      size: ${var.es_ebs_volume_size}
    plugins:
      enabled: true
      installList: [analysis-icu, analysis-kuromoji, analysis-smartcn, analysis-nori, analysis-phonetic]
  EOT
}

resource "rancher2_app_v2" "opensearch-client" {
  #depends_on    = [rancher2_app_v2.opensearch-master]
  count         = var.es_embedded ? 1 : 0
  cluster_id    = data.rancher2_cluster.this.id
  namespace     = rancher2_namespace.this.name
  name          = "opensearch-client"
  repo_name     = "opensearch"
  chart_name    = "opensearch"
  chart_version = "1.14.0"
  force_upgrade = "true"
  values        = <<-EOT
    clusterName: "opensearch-${var.rancher_project_name}"
    masterService: "opensearch-${var.rancher_project_name}"
    nodeGroup: "client"
    replicas: 1
    roles:
      - remote_cluster_client
    persistence:
      enabled: false
    extraEnvs:
      - name: DISABLE_SECURITY_PLUGIN
        value: "true"
    resources:
      requests:
        memory: 1024Mi
      limits:
        memory: 1536Mi
    plugins:
      enabled: true
      installList: [analysis-icu, analysis-kuromoji, analysis-smartcn, analysis-nori, analysis-phonetic]
    ingress:
      hosts:
        - ${join(".", [join("-", [data.rancher2_cluster.this.name, var.rancher_project_name, "opensearch-client"]), var.root_domain])}
      path: /
      enabled: true
      annotations:
        kubernetes.io/ingress.class: alb
        alb.ingress.kubernetes.io/scheme: internet-facing
        alb.ingress.kubernetes.io/group.name: ${local.group_name}
        alb.ingress.kubernetes.io/listen-ports: '[{"HTTPS":443}]'
        alb.ingress.kubernetes.io/success-codes: 200-399

  EOT
}

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
    zone_awareness_enabled   = "true"
    availability_zone_count  = var.es_instance_count
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
    subnet_ids         = slice(data.aws_subnets.private.ids, 0, var.es_instance_count)
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
      kubernetes_cluster = data.rancher2_cluster.this.name
      kubernetes_namespace = var.rancher_project_name
      kubernetes_label_team = var.rancher_project_name
      team = var.rancher_project_name
      kubernetes_service = "ES-Dashboard"
      kubernetes_controller = "ES-${local.env_name}"
  })
}
