# Create a new rancher2 Folio ElasticSearch App in a default Project namespace
resource "rancher2_app" "elasticsearch" {
  count = contains(keys(local.backend-map), "mod-search") && var.folio_embedded_es ? 1 : 0
  depends_on = [
    rancher2_secret.db-connect-modules, rancher2_catalog.folio-charts, rancher2_app.kafka, time_sleep.wait_for_db
  ]
  catalog_name     = "bitnami"
  name             = "elasticsearch"
  description      = "Elasticsearch for mod-search"
  force_upgrade    = "true"
  project_id       = rancher2_project.project.id
  template_name    = "elasticsearch"
  target_namespace = rancher2_namespace.project-namespace.name
  template_version = "14.5.3"
  answers = {
    "coordinating.replicas"                  = "1"
    "coordinating.resources.limits.cpu"      = "512m"
    "coordinating.resources.limits.memory"   = "2048Mi"
    "coordinating.resources.requests.cpu"    = "256m"
    "coordinating.resources.requests.memory" = "1024Mi"
    "data.replicas"                          = "1"
    "data.resources.limits.cpu"              = "512m"
    "data.resources.limits.memory"           = "2048Mi"
    "data.resources.requests.cpu"            = "256m"
    "data.resources.requests.memory"         = "1024Mi"
    "global.coordinating.name"               = rancher2_project.project.name
    "image.debug"                            = "true"
    "master.replicas"                        = "1"
    "master.resources.limits.cpu"            = "512m"
    "master.resources.limits.memory"         = "2048Mi"
    "master.resources.requests.cpu"          = "256m"
    "master.resources.requests.memory"       = "1048Mi"
    "plugins"                                = "analysis-icu, analysis-kuromoji, analysis-smartcn, analysis-nori, analysis-phonetic"
  }
}

#variable "use-es" {
#  default = false
#}
#
#variable "type-es" {
#  default = "service"
#}
#
#variable "es_domain_name" {}
#
#variable "es_version" {
#  default = "7.10"
#}
#
#variable "es_ebs_volumes_size" {
#  default = "500"
#}
#
#variable "es_instance_type" {
#  default = "m5.xlarge.elasticsearch"
#}
#
#variable "es_instance_count" {
#  default = "2"
#}
#
#variable "es_dedicated_master" {
#  default = false
#}
#
#variable "create_service_link_role" {
#  default = false
#}
#
#variable "se_username" {
#  default = "esadmin"
#}
#
#resource "random_password" "se_password" {
#  length = 16
#  special = true
#  number = true
#  upper = true
#  lower = true
#  min_lower = 1
#  min_numeric = 1
#  min_special = 1
#  min_upper = 1
#}
#
#
#resource "aws_security_group" "allow_es" {
#  name = "allow_es-${var.name_prefix}"
#  vpc_id =  module.vpc.vpc_id
#  description = "${var.cluster_name}-${var.name_prefix}-es-service ports"
#  ingress {
#    from_port = 443
#    to_port = 443
#    protocol = "tcp"
#    cidr_blocks = ["0.0.0.0/0"]
#  }
#  egress {
#    from_port = 0
#    to_port = 0
#    protocol = "-1"
#    cidr_blocks = ["0.0.0.0/0"]
#  }
#  tags = {
#    Environment = "dev"
#    Terraform   = "true"
#    Name = "allow_es"
#  }
#
#}
#
#module "aws_es" {
#  source = "github.com/lgallard/terraform-aws-elasticsearch"
#
#  domain_name = "${var.es_domain_name}-${var.name_prefix}"
#  elasticsearch_version = var.es_version
#
#  domain_endpoint_options_enforce_https = true
#  create_service_link_role = var.create_service_link_role
#
#  advanced_options = {
#    "rest.action.multi.allow_explicit_index" = "true"
#  }
#
#  cluster_config = {
#    dedicated_master_enabled = var.es_dedicated_master
#    instance_count = var.es_instance_count
#    instance_type = var.es_instance_type
#    zone_awareness_enabled = "false"
#    availability_zone_count = 2
#    dedicated_master_count = 0
#  }
#
#  advanced_security_options = {
#    enabled = true
#    internal_user_database_enabled = true
#    master_user_options = {
#      master_user_name = var.se_username
#      master_user_password = random_password.se_password.result
#    }
#  }
#
#  ebs_options = {
#    ebs_enabled = var.es_ebs_volumes_size > 0 ? "true" : "false"
#    volume_size = var.es_ebs_volumes_size
#  }
#
#  #  encrypt_at_rest = {
#  #    enabled = "true"
#  #    kms_key_id = "alias/aws/es"
#  #  }
#
#  cognito_options_enabled = false
#
#  cognito_options = {
#    enabled = false
#  }
#
#  vpc_options = {
#    subnet_ids = tolist([module.vpc.private_subnets[0]])
#    security_group_ids = tolist([aws_security_group.allow_es.id])
#  }
#
#  node_to_node_encryption_enabled = true
#  snapshot_options_automated_snapshot_start_hour = 23
#
#  access_policies = templatefile("${path.module}/resources/es-access-policies.tpl", {
#    region = var.aws_region,
#    account = data.aws_caller_identity.current.account_id,
#    domain_name = "${var.es_domain_name}-${var.name_prefix}"
#  })
#
#  timeouts_update = "60m"
#
#  tags = {
#    service = "ElasticSearch"
#    name = "${var.es_domain_name}-${var.name_prefix}"
#    domain = var.es_domain_name
#    version = var.es_version
#  }
#
#}
