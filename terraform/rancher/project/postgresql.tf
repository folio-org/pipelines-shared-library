# Rancher2 Project App Postgres
resource "rancher2_app" "postgres" {
  count            = var.folio_embedded_db ? 1 : 0
  project_id       = rancher2_project.project.id
  target_namespace = rancher2_namespace.project-namespace.name
  catalog_name     = "bitnami"
  name             = "postgres"
  template_name    = "postgresql"
  template_version = "11.0.8"
  answers = {
    "fullnameOverride"                           = "pg-folio"
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
  count  = var.folio_embedded_db ? 0 : 1
  name   = "allow_rds"
  vpc_id = data.aws_eks_cluster.cluster.vpc_config[0].vpc_id
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
  tags = {
    Environment = "rancher"
    Project     = rancher2_project.project.name
    Terraform   = "true"
    Name        = "allow_rds"
  }
}

resource "aws_db_parameter_group" "aurora_db_postgres_parameter_group" {
  count  = var.folio_embedded_db ? 0 : 1
  name   = join("-", ["aurora-db-postgres-parameter-group", data.rancher2_cluster.cluster.name])
  family = join("", ["aurora-postgresql", split(".", var.pg_version)[0]])

  tags = {
    Environment = "rancher"
    Project     = rancher2_project.project.name
    Terraform   = "true"
  }
}

resource "aws_rds_cluster_parameter_group" "aurora_cluster_postgres_parameter_group" {
  count  = var.folio_embedded_db ? 0 : 1
  name   = join("-", ["aurora-postgres-cluster-parameter-group", data.rancher2_cluster.cluster.name])
  family = join("", ["aurora-postgresql", split(".", var.pg_version)[0]])

  tags = {
    Environment = "rancher"
    Project     = rancher2_project.project.name
    Terraform   = "true"
  }
}

#Module for AWS RDS instance creation
module "rds" {
  count                           = var.folio_embedded_db ? 0 : 1
  source                          = "terraform-aws-modules/rds-aurora/aws"
  version                         = "~> 3.0"
  name                            = join("-", [data.rancher2_cluster.cluster.name, rancher2_project.project.name])
  engine                          = "aurora-postgresql"
  engine_version                  = var.pg_version
  vpc_id                          = data.aws_eks_cluster.cluster.vpc_config[0].vpc_id
  subnets                         = data.aws_subnets.db_subnet.ids
  replica_count                   = 1
  database_name                   = var.pg_dbname
  username                        = var.pg_username
  password                        = var.pg_password
  instance_type                   = "db.r5.xlarge"
  storage_encrypted               = true
  apply_immediately               = true
  vpc_security_group_ids          = [aws_security_group.allow_rds[count.index].id]
  db_parameter_group_name         = aws_db_parameter_group.aurora_db_postgres_parameter_group[count.index].id
  db_cluster_parameter_group_name = aws_rds_cluster_parameter_group.aurora_cluster_postgres_parameter_group[count.index].id
  #  snapshot_identifier             = var.db_snapshot_arn == "" ? local.db_snapshot_arn : var.db_snapshot_arn
  #  create_random_password          = var.db_password == "" ? true : false
  publicly_accessible = true
  skip_final_snapshot = true

  tags = {
    Environment = "rancher"
    Project     = rancher2_project.project.name
    Terraform   = "true"
  }
}

# Create a new rancher2 PgAdmin4 App in a default Project namespace
resource "rancher2_app" "pgadmin4" {
  project_id       = rancher2_project.project.id
  target_namespace = rancher2_namespace.project-namespace.name
  catalog_name = join(":", [
    element(split(":", rancher2_project.project.id), 1), rancher2_catalog.folio-charts.name
  ])
  name          = "pgadmin4"
  description   = "PgAdmin app"
  template_name = "pgadmin4"
  answers = {
    "env.email"                                                  = var.pgadmin_username
    "env.password"                                               = var.pgadmin_password
    "service.type"                                               = "NodePort"
    "ingress.enabled"                                            = "true"
    "ingress.annotations.kubernetes\\.io/ingress\\.class"        = "alb"
    "ingress.annotations.alb\\.ingress\\.kubernetes\\.io/scheme" = "internet-facing"
    "ingress.annotations.alb\\.ingress\\.kubernetes\\.io/group\\.name" = join(".", [
      data.rancher2_cluster.cluster.name, rancher2_project.project.name
    ])
    "ingress.annotations.alb\\.ingress\\.kubernetes\\.io/listen-ports"  = "[{\"HTTPS\":443}]"
    "ingress.annotations.alb\\.ingress\\.kubernetes\\.io/success-codes" = "200-399"
    "ingress.hosts[0].paths[0]"                                         = "/*"
    "ingress.hosts[0].host" = join(".", [
      join("-", [data.rancher2_cluster.cluster.name, rancher2_project.project.name, "pgadmin"]), var.root_domain
    ])
  }
}
