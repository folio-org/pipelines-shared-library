resource "random_password" "pg_password" {
  length           = 16
  special          = true
  number           = true
  upper            = true
  lower            = true
  min_lower        = 2
  min_numeric      = 2
  min_special      = 2
  min_upper        = 2
  override_special = "‘~!@#$%^&*()_-+={}[]\\/<>,.;?':|"
}

locals {
  pg_password = var.pg_password == "" ? random_password.pg_password.result : var.pg_password
}

# Rancher2 Project App Postgres
resource "rancher2_app" "postgres" {
  count            = var.pg_embedded ? 1 : 0
  project_id       = rancher2_project.this.id
  target_namespace = rancher2_namespace.this.name
  catalog_name     = "bitnami"
  name             = "postgresql"
  template_name    = "postgresql"
  template_version = "11.0.8"
  answers = {
    "image.tag"                                  = join(".", [var.pg_version, "0"])
    "auth.database"                              = var.pg_dbname
    "auth.postgresPassword"                      = var.pg_password
    "primary.persistence.enabled"                = "true"
    "primary.persistence.size"                   = "20Gi"
    "primary.persistence.storageClass"           = "gp2"
    "primary.resources.limits.cpu"               = "1000m"
    "primary.resources.limits.memory"            = "3072Mi"
    "primary.resources.requests.cpu"             = "1000m"
    "primary.resources.requests.memory"          = "2048Mi"
    "primary.podSecurityContext.fsGroup"         = "1001"
    "primary.containerSecurityContext.runAsUser" = "1001"
    "primary.extendedConfiguration"              = <<-EOT
      shared_buffers = '2048MB'
      max_connections = '1000'
      listen_addresses = '0.0.0.0'
    EOT
    "volumePermissions.enabled"                  = "true"
  }
}

# Delay for db initialization
resource "time_sleep" "wait_for_db" {
  depends_on      = [rancher2_app.postgres]
  create_duration = "30s"
}

#Security group for RDS instance
resource "aws_security_group" "allow_rds" {
  count       = var.pg_embedded ? 0 : 1
  name        = "allow-rds-${local.env_name}"
  description = "Allow connection to RDS"
  vpc_id      = data.aws_eks_cluster.this.vpc_config[0].vpc_id

  ingress {
    from_port   = 5432
    to_port     = 5432
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
      Name = "allow-rds"
  })
}

resource "aws_db_parameter_group" "aurora_db_postgres_parameter_group" {
  count  = var.pg_embedded ? 0 : 1
  name   = join("-", ["aurora-db-postgres-parameter-group", local.env_name])
  family = join("", ["aurora-postgresql", split(".", var.pg_version)[0]])
  tags   = var.tags
}

resource "aws_rds_cluster_parameter_group" "aurora_cluster_postgres_parameter_group" {
  count  = var.pg_embedded ? 0 : 1
  name   = join("-", ["aurora-postgres-cluster-parameter-group", local.env_name])
  family = join("", ["aurora-postgresql", split(".", var.pg_version)[0]])
  tags   = var.tags
}

#Module for AWS RDS instance creation
module "rds" {
  count                           = var.pg_embedded ? 0 : 1
  source                          = "terraform-aws-modules/rds-aurora/aws"
  version                         = "~> 3.0"
  name                            = "rds-${local.env_name}"
  engine                          = "aurora-postgresql"
  engine_version                  = var.pg_version
  vpc_id                          = data.aws_eks_cluster.this.vpc_config[0].vpc_id
  subnets                         = data.aws_subnets.database.ids
  replica_count                   = 1
  database_name                   = var.pg_dbname
  username                        = var.pg_username
  password                        = local.pg_password
  instance_type                   = var.pg_instance_type
  storage_encrypted               = true
  apply_immediately               = true
  vpc_security_group_ids          = [aws_security_group.allow_rds[count.index].id]
  db_parameter_group_name         = aws_db_parameter_group.aurora_db_postgres_parameter_group[count.index].id
  db_cluster_parameter_group_name = aws_rds_cluster_parameter_group.aurora_cluster_postgres_parameter_group[count.index].id
  snapshot_identifier             = var.pg_rds_snapshot_name == "" ? "" : local.db_snapshot_arn
  create_random_password          = false
  publicly_accessible             = true
  skip_final_snapshot             = true

  tags = merge(
    var.tags,
    {
      service = "RDS"
      name    = "rds-${local.env_name}"
      version = var.pg_version
  })
}

# Create a new rancher2 PgAdmin4 App in a default Project namespace
resource "rancher2_app" "pgadmin4" {
  project_id       = rancher2_project.this.id
  target_namespace = rancher2_namespace.this.name
  catalog_name = join(":", [
    element(split(":", rancher2_project.this.id), 1), rancher2_catalog.folio-charts.name
  ])
  name          = "pgadmin4"
  description   = "PgAdmin app"
  template_name = "pgadmin4"
  template_version = "1.9.11"

  answers = {
    "env.email"                                                  = var.pgadmin_username
    "env.password"                                               = var.pgadmin_password
    "service.type"                                               = "NodePort"
    "ingress.enabled"                                            = "true"
    "ingress.annotations.kubernetes\\.io/ingress\\.class"        = "alb"
    "ingress.annotations.alb\\.ingress\\.kubernetes\\.io/scheme" = "internet-facing"
    "ingress.annotations.alb\\.ingress\\.kubernetes\\.io/group\\.name" = join(".", [
      data.rancher2_cluster.this.name, rancher2_project.this.name
    ])
    "ingress.annotations.alb\\.ingress\\.kubernetes\\.io/listen-ports"  = "[{\"HTTPS\":443}]"
    "ingress.annotations.alb\\.ingress\\.kubernetes\\.io/success-codes" = "200-399"
    "ingress.hosts[0].paths[0]"                                         = "/*"
    "ingress.hosts[0].host" = join(".", [
      join("-", [data.rancher2_cluster.this.name, rancher2_project.this.name, "pgadmin"]), var.root_domain
    ])
  }
}
