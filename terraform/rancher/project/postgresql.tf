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

resource "rancher2_secret" "db-credentials" {
  name         = "db-credentials"
  project_id   = rancher2_project.this.id
  namespace_id = rancher2_namespace.this.id
  data = merge({
    ENV             = base64encode(local.env_name)
    DB_HOST         = base64encode(var.pg_embedded ? local.pg_service_writer : module.rds[0].cluster_endpoint)
    DB_PORT         = base64encode("5432")
    DB_USERNAME     = base64encode(var.pg_embedded ? var.pg_username : module.rds[0].cluster_master_username)
    DB_PASSWORD     = base64encode(local.pg_password)
    DB_DATABASE     = base64encode(var.eureka ? local.pg_eureka_db_name : var.pg_dbname)
    DB_MAXPOOLSIZE  = base64encode("50")
    DB_CHARSET      = base64encode("UTF-8")
    DB_QUERYTIMEOUT = base64encode("60000")
    },
    var.enable_rw_split ? {
      DB_HOST_READER = base64encode(var.pg_embedded ? local.pg_service_reader : module.rds[0].cluster_reader_endpoint)
      DB_PORT_READER = base64encode("5432")
  } : {})
}

locals {
  pg_password       = var.pg_password == "" ? random_password.pg_password.result : var.pg_password
  pg_architecture   = var.enable_rw_split ? "replication" : "standalone"
  pg_service_reader = var.enable_rw_split ? "postgresql-${var.rancher_project_name}-read" : ""
  pg_service_writer = var.enable_rw_split ? "postgresql-${var.rancher_project_name}-primary" : "postgresql-${var.rancher_project_name}"
  pg_auth           = local.pg_architecture == "replication" ? "false" : "true"
  pg_eureka_db_name = var.eureka ? "folio" : var.pg_dbname
}

resource "kubectl_manifest" "postgresql-pdb" {
  count              = var.pg_embedded ? 1 : 0
  provider           = kubectl
  override_namespace = rancher2_namespace.this.name
  yaml_body          = <<YAML
apiVersion: policy/v1
kind: PodDisruptionBudget
metadata:
  name: postgresql-pdb
spec:
  minAvailable: 1
  selector:
    matchLabels:
      app.kubernetes.io/name: postgresql
      app.kubernetes.io/instance: postgresql-${var.rancher_project_name}
YAML
}

# PostgreSQL database deployment
resource "helm_release" "postgresql" {
  depends_on = [rancher2_secret.s3-postgres-backups-credentials, rancher2_secret.db-credentials]
  count      = var.pg_embedded ? 1 : 0
  namespace  = rancher2_namespace.this.name
  name       = "postgresql-${var.rancher_project_name}"
  repository = local.catalogs.bitnami
  chart      = "postgresql"
  version    = "13.2.19"
  values = [<<-EOF
architecture: ${local.pg_architecture}
readReplicas:
  replicaCount: 1
  resources:
    requests:
      memory: 8192Mi
    limits:
      memory: 10240Mi
  podAffinityPreset: hard
  persistence:
    enabled: true
    size: '${var.pg_vol_size}Gi'
    storageClass: gp2
  extendedConfiguration: |-
    shared_buffers = '3096MB'
    max_connections = '${var.pg_max_conn}'
    listen_addresses = '0.0.0.0'
    effective_cache_size = '7680MB'
    maintenance_work_mem = '640MB'
    checkpoint_completion_target = '0.9'
    wal_buffers = '16MB'
    default_statistics_target = '100'
    random_page_cost = '1.1'
    effective_io_concurrency = '200'
    work_mem = '3096kB'
    min_wal_size = '1GB'
    max_wal_size = '4GB'
  ${indent(2, local.schedule_value)}
image:
  registry: 732722833398.dkr.ecr.us-west-2.amazonaws.com
  repository: postgresql
  tag: ${join(".", [var.pg_version, "0"])}
  pullPolicy: IfNotPresent
auth:
  database: ${local.pg_eureka_db_name}
  postgresPassword: ${var.pg_password}
  replicationPassword: ${var.pg_password}
  replicationUsername: ${var.pg_username}
  usePasswordFiles: ${local.pg_auth}
primary:
  initdb:
    scripts:
      init.sql: |
        ${indent(8, var.eureka ? templatefile("${path.module}/resources/eureka.db.tpl", { dbs = ["kong", "keycloak"], pg_password = var.pg_password }) : "--fail safe")}
        CREATE DATABASE ldp;
        CREATE USER ldpadmin PASSWORD '${var.pg_ldp_user_password}';
        CREATE USER ldpconfig PASSWORD '${var.pg_ldp_user_password}';
        CREATE USER ldp PASSWORD '${var.pg_ldp_user_password}';
        ALTER DATABASE ldp OWNER TO ldpadmin;
        ALTER DATABASE ldp SET search_path TO public;
        REVOKE CREATE ON SCHEMA public FROM public;
        GRANT ALL ON SCHEMA public TO ldpadmin;
        GRANT USAGE ON SCHEMA public TO ldpconfig;
        GRANT USAGE ON SCHEMA public TO ldp;
  persistence:
    enabled: true
    size: '${var.pg_vol_size}Gi'
    storageClass: gp2
  resources:
    requests:
      memory: 8192Mi
    limits:
      memory: 10240Mi
  podSecurityContext:
    fsGroup: 1001
  containerSecurityContext:
    runAsUser: 1001
  podAffinityPreset: hard
  extendedConfiguration: |-
    shared_buffers = '3096MB'
    max_connections = '${var.pg_max_conn}'
    listen_addresses = '0.0.0.0'
    effective_cache_size = '7680MB'
    maintenance_work_mem = '640MB'
    checkpoint_completion_target = '0.9'
    wal_buffers = '16MB'
    default_statistics_target = '100'
    random_page_cost = '1.1'
    effective_io_concurrency = '200'
    work_mem = '3096kB'
    min_wal_size = '1GB'
    max_wal_size = '4GB'
  ${indent(2, local.schedule_value)}
volumePermissions:
  enabled: true
  image:
    registry: 732722833398.dkr.ecr.us-west-2.amazonaws.com
    repository: os-shell
    tag: 11-debian-11-r91
    pullPolicy: IfNotPresent
metrics:
  enabled: false
  resources:
    requests:
      memory: 1024Mi
    limits:
      memory: 3072Mi
  serviceMonitor:
    enabled: true
    namespace: monitoring
    interval: 30s
    scrapeTimeout: 30s
EOF
  ]
}

resource "time_sleep" "ram_resource_propagation" {
  count           = var.pg_embedded ? 1 : 0
  create_duration = "30s"

  triggers = {
    resource_id = helm_release.postgresql[0].id
  }
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
  count          = var.pg_embedded ? 0 : 1
  source         = "terraform-aws-modules/rds-aurora/aws"
  version        = "~>8.0"
  name           = "rds-${local.env_name}"
  engine         = "aurora-postgresql"
  engine_version = var.pg_version

  instances = var.enable_rw_split ? {
    for i in ["read", "write"] :
    i => {
      instance_class      = var.pg_instance_type
      publicly_accessible = true
    }
    } : {
    write = {
      instance_class      = var.pg_instance_type
      publicly_accessible = true
    }
  }

  vpc_id                          = data.aws_eks_cluster.this.vpc_config[0].vpc_id
  subnets                         = data.aws_subnets.database.ids
  db_subnet_group_name            = "folio-rancher-vpc"
  database_name                   = local.pg_eureka_db_name
  master_username                 = var.pg_username
  master_password                 = local.pg_password
  manage_master_user_password     = false
  storage_encrypted               = true
  apply_immediately               = true
  vpc_security_group_ids          = [aws_security_group.allow_rds[count.index].id]
  db_parameter_group_name         = aws_db_parameter_group.aurora_db_postgres_parameter_group[count.index].id
  db_cluster_parameter_group_name = aws_rds_cluster_parameter_group.aurora_cluster_postgres_parameter_group[count.index].id
  snapshot_identifier             = var.pg_rds_snapshot_name == "" ? "" : local.db_snapshot_arn
  skip_final_snapshot             = true

  tags = merge(
    var.tags,
    {
      service               = "RDS"
      name                  = "rds-${local.env_name}"
      version               = var.pg_version
      devTeam               = var.rancher_project_name
      kubernetes_cluster    = data.rancher2_cluster.this.name
      kubernetes_namespace  = var.rancher_project_name
      kubernetes_label_team = var.rancher_project_name
      kubernetes_service    = "RDS-Database"
  })
}

resource "postgresql_role" "kong" {
  count      = var.eureka && !var.pg_embedded ? 1 : 0
  name       = "kong"
  login      = true
  password   = local.pg_password
  depends_on = [module.rds]
  connection {
    host     = module.rds[0].cluster_endpoint
    port     = 5432
    username = module.rds[0].cluster_master_username
    password = local.pg_password
  }
}

resource "postgresql_role" "keycloak" {
  count      = var.eureka && !var.pg_embedded ? 1 : 0
  name       = "keycloak"
  login      = true
  password   = local.pg_password
  depends_on = [module.rds]
  connection {
    host     = module.rds[0].cluster_endpoint
    port     = 5432
    username = module.rds[0].cluster_master_username
    password = local.pg_password
  }
}

resource "postgresql_database" "eureka_kong" {
  depends_on = [postgresql_role.kong]
  count      = var.eureka && !var.pg_embedded ? 1 : 0
  name       = "kong"
  owner      = "kong"
  connection {
    host     = module.rds[0].cluster_endpoint
    port     = 5432
    username = module.rds[0].cluster_master_username
    password = local.pg_password
  }
}

resource "postgresql_database" "eureka_keycloak" {
  depends_on = [postgresql_role.keycloak]
  count      = var.eureka && !var.pg_embedded ? 1 : 0
  name       = "keycloak"
  owner      = "keycloak"
  connection {
    host     = module.rds[0].cluster_endpoint
    port     = 5432
    username = module.rds[0].cluster_master_username
    password = local.pg_password
  }
}

# pgAdmin service deployment
resource "helm_release" "pgadmin" {
  count      = var.pgadmin4 ? 1 : 0
  namespace  = rancher2_namespace.this.name
  repository = local.catalogs.runix
  name       = "pgadmin4"
  chart      = "pgadmin4"
  version    = "1.10.1"
  values = [<<-EOF
image:
  tag: 9.5.0
  registry: 732722833398.dkr.ecr.us-west-2.amazonaws.com
  repository: pgadmin4
  pullPolicy: IfNotPresent
resources:
  requests:
    memory: 256Mi
  limits:
    memory: 512Mi
env:
  email: ${var.pgadmin_username}
  password: ${var.pgadmin_password}
  variables:
    - name: PGPASSWORD
      value: ${var.pg_password}
    - name: PGUSER
      value: ${var.pg_embedded ? var.pg_username : module.rds[0].cluster_master_username}
    - name: PGHOST
      value: ${var.pg_embedded ? local.pg_service_writer : module.rds[0].cluster_endpoint}
    - name: PGDATABASE
      value: ${local.pg_eureka_db_name}
    - name: PGPORT
      value: '5432'
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
      Username: ${var.pg_embedded ? var.pg_username : module.rds[0].cluster_master_username}
      Host: ${var.pg_embedded ? local.pg_service_writer : module.rds[0].cluster_endpoint}
      SSLMode: prefer
      MaintenanceDB: ${local.pg_eureka_db_name}
${local.schedule_value}
EOF
  ]
}
