resource "random_password" "pg_password" {
  length           = 16
  special          = true
  numeric          = true
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
resource "rancher2_app_v2" "postgresql" {
  depends_on    = [rancher2_secret.s3-postgres-backups-credentials, rancher2_secret.db-connect-modules]
  count         = var.pg_embedded ? 1 : 0
  cluster_id    = data.rancher2_cluster.this.id
  namespace     = rancher2_namespace.this.name
  name          = "postgresql"
  repo_name     = "bitnami"
  chart_name    = "postgresql"
  chart_version = "11.0.8"
  force_upgrade = "true"
  values        = <<-EOT
    image:
      tag: ${join(".", [var.pg_version, "0"])}
    auth:
      database: ${var.pg_dbname}
      postgresPassword: ${var.pg_password}
    primary:
      persistence:
        enabled: true
        size: 20Gi
        storageClass: gp2
      resources:
        requests:
          memory: 512Mi
        limits:
          memory: 6144Mi
      podSecurityContext:
        fsGroup: 1001
      containerSecurityContext:
        runAsUser: 1001
      extendedConfiguration: |-
        shared_buffers = '2048MB'
        max_connections = '1000'
        listen_addresses = '0.0.0.0'
    volumePermissions:
      enabled: true
  EOT
}

# Delay for db initialization
resource "time_sleep" "wait_for_db" {
  depends_on      = [rancher2_app_v2.postgresql]
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
      devTeam = var.rancher_project_name
      kubernetes_cluster = data.rancher2_cluster.this.name
      kubernetes_namespace = var.rancher_project_name
      kubernetes_label_team = var.rancher_project_name
      team = var.rancher_project_name
      kubernetes_service = "RDS-Database"
      kubernetes_controller = "RDS-${local.env_name}"
  })
}

# Create a new rancher2 PgAdmin4 App in a default Project namespace
resource "rancher2_app_v2" "pgadmin4" {
  count         = var.pgadmin4 ? 1 : 0
  cluster_id    = data.rancher2_cluster.this.id
  namespace     = rancher2_namespace.this.name
  name          = "pgadmin4"
  repo_name     = "runix"
  chart_name    = "pgadmin4"
  chart_version = "1.10.1"
  force_upgrade = "true"
  values        = <<-EOT
    env:
      email: ${var.pgadmin_username}
      password: ${var.pgadmin_password}
    service:
      type: NodePort
    ingress:
      hosts:
        - host: ${join(".", [join("-", [data.rancher2_cluster.this.name, var.rancher_project_name, "pgadmin"]), var.root_domain])}
          paths:
            - path: /*
              pathType: ImplementationSpecific
      enabled: true
      annotations:
        kubernetes.io/ingress.class: alb
        alb.ingress.kubernetes.io/scheme: internet-facing
        alb.ingress.kubernetes.io/group.name: ${local.group_name}
        alb.ingress.kubernetes.io/listen-ports: '[{"HTTPS":443}]'
        alb.ingress.kubernetes.io/success-codes: 200-399
        alb.ingress.kubernetes.io/healthcheck-path: /misc/ping
        alb.ingress.kubernetes.io/healthcheck-port: '80'

    serverDefinitions:
      enabled: true
      servers:
        pg:
          Name: ${var.rancher_project_name}
          Group: Servers
          Port: 5432
          Username: ${var.pg_embedded ? var.pg_username : module.rds[0].this_rds_cluster_master_username}
          Host: ${var.pg_embedded ? "postgresql" : module.rds[0].this_rds_cluster_endpoint}
          SSLMode: prefer
          MaintenanceDB: ${var.pg_dbname}
  EOT
}
