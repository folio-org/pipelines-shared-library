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

resource "rancher2_secret" "db-credentials-eureka-components" {
  count        = contains(["cikarate", "cicypress", "cypress", "karate"], var.rancher_project_name) ? 1 : 0
  name         = "db-credentials-${var.rancher_project_name}-eureka"
  project_id   = rancher2_project.this.id
  namespace_id = rancher2_namespace.this.id
  data = merge({
    ENV             = base64encode(local.env_name)
    DB_HOST         = base64encode("postgresql-${var.rancher_project_name}-eureka")
    DB_PORT         = base64encode("5432")
    DB_USERNAME     = base64encode(var.pg_username)
    DB_PASSWORD     = base64encode(var.pg_password == "" ? random_password.pg_password.result : var.pg_password)
    DB_DATABASE     = base64encode(var.eureka ? "folio" : var.pg_dbname)
    DB_MAXPOOLSIZE  = base64encode("50")
    DB_CHARSET      = base64encode("UTF-8")
    DB_QUERYTIMEOUT = base64encode("60000")
    },
    var.enable_rw_split ? {
      DB_HOST_READER = base64encode("postgresql-${var.rancher_project_name}-eureka-read")
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
  version    = "16.7.27"
  values = [<<-EOF
global:
  security:
    allowInsecureImages: true
kubeVersion: ""
nameOverride: ""
fullnameOverride: ""
namespaceOverride: ""
clusterDomain: "cluster.local"
image:
  registry: 732722833398.dkr.ecr.us-west-2.amazonaws.com
  repository: postgresql
  tag: ${join(".", [var.pg_version, "0"])}
  pullPolicy: IfNotPresent
auth:
  enablePostgresUser: true
  postgresPassword: ${base64decode(rancher2_secret.db-credentials.data.DB_PASSWORD)}
  username: ${base64decode(rancher2_secret.db-credentials.data.DB_USERNAME)}
  password: ${base64decode(rancher2_secret.db-credentials.data.DB_PASSWORD)}
  database: ${base64decode(rancher2_secret.db-credentials.data.DB_DATABASE)}
  replicationUsername: ${var.pg_username}
  replicationPassword: ${var.pg_password}
  usePasswordFiles: ${local.pg_auth}
architecture: ${local.pg_architecture}
primary:
  name: main
  resources:
    requests:
      memory: 8Gi
    limits:
      memory: 10Gi
  persistence:
    enabled: true
    size: '${var.pg_vol_size}Gi'
    storageClass: gp2      
  initdb:
    scripts:
      init.sql: |
        ${indent(8, var.eureka ? templatefile("${path.module}/resources/eureka.db.tpl", { dbs = [local.pg_eureka_db_name, "kong", "keycloak"], pg_password = var.pg_password }) : "--fail safe")}
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
      configure-postgres.sh: |
        #!/bin/bash
        echo "Configuring PostgreSQL settings optimized for JSONB operations..."
        # Memory settings optimized for JSONB
        sed -i "s/#*shared_buffers = .*/shared_buffers = 3096MB/" /bitnami/postgresql/data/postgresql.conf
        sed -i "s/#*max_connections = .*/max_connections = 5000/" /bitnami/postgresql/data/postgresql.conf
        sed -i "s/#*listen_addresses = .*/listen_addresses = '0.0.0.0'/" /bitnami/postgresql/data/postgresql.conf
        sed -i "s/#*effective_cache_size = .*/effective_cache_size = 7680MB/" /bitnami/postgresql/data/postgresql.conf
        sed -i "s/#*maintenance_work_mem = .*/maintenance_work_mem = 1GB/" /bitnami/postgresql/data/postgresql.conf
        sed -i "s/#*work_mem = .*/work_mem = 8MB/" /bitnami/postgresql/data/postgresql.conf
        
        # I/O and storage settings optimized for JSONB
        sed -i "s/#*random_page_cost = .*/random_page_cost = 1.1/" /bitnami/postgresql/data/postgresql.conf
        sed -i "s/#*seq_page_cost = .*/seq_page_cost = 1.0/" /bitnami/postgresql/data/postgresql.conf
        sed -i "s/#*effective_io_concurrency = .*/effective_io_concurrency = 200/" /bitnami/postgresql/data/postgresql.conf
        sed -i "s/#*maintenance_io_concurrency = .*/maintenance_io_concurrency = 200/" /bitnami/postgresql/data/postgresql.conf
        
        # WAL settings for better write performance with JSONB
        sed -i "s/#*wal_buffers = .*/wal_buffers = 32MB/" /bitnami/postgresql/data/postgresql.conf
        sed -i "s/#*min_wal_size = .*/min_wal_size = 2GB/" /bitnami/postgresql/data/postgresql.conf
        sed -i "s/#*max_wal_size = .*/max_wal_size = 8GB/" /bitnami/postgresql/data/postgresql.conf
        sed -i "s/#*wal_compression = .*/wal_compression = on/" /bitnami/postgresql/data/postgresql.conf
        sed -i "s/#*checkpoint_completion_target = .*/checkpoint_completion_target = 0.9/" /bitnami/postgresql/data/postgresql.conf
        sed -i "s/#*checkpoint_timeout = .*/checkpoint_timeout = 15min/" /bitnami/postgresql/data/postgresql.conf
        
        # Query planner settings optimized for JSONB
        sed -i "s/#*default_statistics_target = .*/default_statistics_target = 1000/" /bitnami/postgresql/data/postgresql.conf
        sed -i "s/#*cpu_tuple_cost = .*/cpu_tuple_cost = 0.01/" /bitnami/postgresql/data/postgresql.conf
        sed -i "s/#*cpu_index_tuple_cost = .*/cpu_index_tuple_cost = 0.005/" /bitnami/postgresql/data/postgresql.conf
        sed -i "s/#*cpu_operator_cost = .*/cpu_operator_cost = 0.0025/" /bitnami/postgresql/data/postgresql.conf
        
        # Background writer settings for better I/O
        sed -i "s/#*bgwriter_delay = .*/bgwriter_delay = 200ms/" /bitnami/postgresql/data/postgresql.conf
        sed -i "s/#*bgwriter_lru_maxpages = .*/bgwriter_lru_maxpages = 100/" /bitnami/postgresql/data/postgresql.conf
        sed -i "s/#*bgwriter_lru_multiplier = .*/bgwriter_lru_multiplier = 2.0/" /bitnami/postgresql/data/postgresql.conf
        sed -i "s/#*bgwriter_flush_after = .*/bgwriter_flush_after = 512kB/" /bitnami/postgresql/data/postgresql.conf
        
        # JSONB-specific optimizations
        sed -i "s/#*gin_fuzzy_search_limit = .*/gin_fuzzy_search_limit = 0/" /bitnami/postgresql/data/postgresql.conf
        sed -i "s/#*gin_pending_list_limit = .*/gin_pending_list_limit = 8MB/" /bitnami/postgresql/data/postgresql.conf
        
        # Parallel processing for JSONB operations
        sed -i "s/#*max_parallel_workers_per_gather = .*/max_parallel_workers_per_gather = 4/" /bitnami/postgresql/data/postgresql.conf
        sed -i "s/#*max_parallel_workers = .*/max_parallel_workers = 8/" /bitnami/postgresql/data/postgresql.conf
        sed -i "s/#*max_parallel_maintenance_workers = .*/max_parallel_maintenance_workers = 4/" /bitnami/postgresql/data/postgresql.conf
        
        # Vacuum and autovacuum settings for JSONB tables
        sed -i "s/#*autovacuum_vacuum_scale_factor = .*/autovacuum_vacuum_scale_factor = 0.1/" /bitnami/postgresql/data/postgresql.conf
        sed -i "s/#*autovacuum_analyze_scale_factor = .*/autovacuum_analyze_scale_factor = 0.05/" /bitnami/postgresql/data/postgresql.conf
        sed -i "s/#*autovacuum_vacuum_cost_limit = .*/autovacuum_vacuum_cost_limit = 2000/" /bitnami/postgresql/data/postgresql.conf
        sed -i "s/#*autovacuum_max_workers = .*/autovacuum_max_workers = 6/" /bitnami/postgresql/data/postgresql.conf
        
        # Monitoring and logging
        sed -i "s/#*shared_preload_libraries = .*/shared_preload_libraries = 'auto_explain,pg_stat_statements'/" /bitnami/postgresql/data/postgresql.conf
        sed -i "s/#*auto_explain.log_min_duration = .*/auto_explain.log_min_duration = '1s'/" /bitnami/postgresql/data/postgresql.conf
        sed -i "s/#*auto_explain.log_analyze = .*/auto_explain.log_analyze = on/" /bitnami/postgresql/data/postgresql.conf
        sed -i "s/#*auto_explain.log_buffers = .*/auto_explain.log_buffers = on/" /bitnami/postgresql/data/postgresql.conf
        sed -i "s/#*auto_explain.log_timing = .*/auto_explain.log_timing = on/" /bitnami/postgresql/data/postgresql.conf
        sed -i "s/#*auto_explain.log_verbose = .*/auto_explain.log_verbose = on/" /bitnami/postgresql/data/postgresql.conf
        sed -i "s/#*pg_stat_statements.track = .*/pg_stat_statements.track = all/" /bitnami/postgresql/data/postgresql.conf
        sed -i "s/#*pg_stat_statements.max = .*/pg_stat_statements.max = 10000/" /bitnami/postgresql/data/postgresql.conf
        
        # JIT compilation for complex JSONB queries (PostgreSQL 11+)
        sed -i "s/#*jit = .*/jit = on/" /bitnami/postgresql/data/postgresql.conf
        sed -i "s/#*jit_above_cost = .*/jit_above_cost = 100000/" /bitnami/postgresql/data/postgresql.conf
        sed -i "s/#*jit_inline_above_cost = .*/jit_inline_above_cost = 500000/" /bitnami/postgresql/data/postgresql.conf
        sed -i "s/#*jit_optimize_above_cost = .*/jit_optimize_above_cost = 500000/" /bitnami/postgresql/data/postgresql.conf
        echo "PostgreSQL configuration updated"
  containerSecurityContext:
    enabled: true
    runAsUser: 1001
    readOnlyRootFilesystem: false
volumePermissions:
  enabled: true
  image:
    registry: 732722833398.dkr.ecr.us-west-2.amazonaws.com
    repository: os-shell
    tag: 12-debian-12-r51
    pullPolicy: IfNotPresent
  resourcesPreset: "nano"
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

resource "helm_release" "postgresql_qg" {
  depends_on = [rancher2_secret.s3-postgres-backups-credentials, rancher2_secret.db-credentials-eureka-components]
  count      = var.pg_embedded && contains(["cikarate", "cicypress", "cypress", "karate"], var.rancher_project_name) ? 1 : 0
  namespace  = rancher2_namespace.this.name
  name       = "postgresql-${var.rancher_project_name}-eureka"
  repository = local.catalogs.bitnami
  chart      = "postgresql"
  version    = "16.7.27"
  values = [<<-EOF
global:
  security:
    allowInsecureImages: true
kubeVersion: ""
nameOverride: ""
fullnameOverride: ""
namespaceOverride: ""
clusterDomain: "cluster.local"
image:
  registry: 732722833398.dkr.ecr.us-west-2.amazonaws.com
  repository: postgresql
  tag: ${join(".", [var.pg_version, "0"])}
  pullPolicy: IfNotPresent
auth:
  enablePostgresUser: true
  postgresPassword: ${base64decode(rancher2_secret.db-credentials-eureka-components[0].data.DB_PASSWORD)}
  username: ${base64decode(rancher2_secret.db-credentials-eureka-components[0].data.DB_USERNAME)}
  password: ${base64decode(rancher2_secret.db-credentials-eureka-components[0].data.DB_PASSWORD)}
  database: ${base64decode(rancher2_secret.db-credentials-eureka-components[0].data.DB_DATABASE)}
  replicationUsername: ${var.pg_username}
  replicationPassword: ${var.pg_password}
  usePasswordFiles: ${var.enable_rw_split ? "false" : "true"}
architecture: ${var.enable_rw_split ? "replication" : "standalone"}
primary:
  name: main
  resources:
    requests:
      memory: 2Gi
    limits:
      memory: 4Gi
  initdb:
    scripts:
      init.sql: |
        ${indent(8, var.eureka ? templatefile("${path.module}/resources/eureka.db.tpl", { dbs = [var.eureka ? "folio" : var.pg_dbname, "kong", "keycloak"], pg_password = var.pg_password }) : "--fail safe")}
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
      configure-postgres.sh: |
        #!/bin/bash
        echo "Configuring PostgreSQL settings..."
        sed -i "s/#*max_connections = .*/max_connections = ${var.pg_max_conn}/" /bitnami/postgresql/data/postgresql.conf
        sed -i "s/#*max_parallel_workers_per_gather = .*/max_parallel_workers_per_gather = 0/" /bitnami/postgresql/data/postgresql.conf
        sed -i "s/#*shared_buffers = .*/shared_buffers = 1024MB/" /bitnami/postgresql/data/postgresql.conf
        sed -i "s/#*listen_addresses = .*/listen_addresses = '0.0.0.0'/" /bitnami/postgresql/data/postgresql.conf
        sed -i "s/#*effective_cache_size = .*/effective_cache_size = 3072MB/" /bitnami/postgresql/data/postgresql.conf
        sed -i "s/#*maintenance_work_mem = .*/maintenance_work_mem = 128MB/" /bitnami/postgresql/data/postgresql.conf
        sed -i "s/#*checkpoint_completion_target = .*/checkpoint_completion_target = 0.9/" /bitnami/postgresql/data/postgresql.conf
        sed -i "s/#*wal_buffers = .*/wal_buffers = 16MB/" /bitnami/postgresql/data/postgresql.conf
        sed -i "s/#*default_statistics_target = .*/default_statistics_target = 100/" /bitnami/postgresql/data/postgresql.conf
        sed -i "s/#*random_page_cost = .*/random_page_cost = 1.1/" /bitnami/postgresql/data/postgresql.conf
        sed -i "s/#*effective_io_concurrency = .*/effective_io_concurrency = 200/" /bitnami/postgresql/data/postgresql.conf
        sed -i "s/#*work_mem = .*/work_mem = 2MB/" /bitnami/postgresql/data/postgresql.conf
        sed -i "s/#*min_wal_size = .*/min_wal_size = 1GB/" /bitnami/postgresql/data/postgresql.conf
        sed -i "s/#*max_wal_size = .*/max_wal_size = 4GB/" /bitnami/postgresql/data/postgresql.conf
        echo "PostgreSQL configuration updated"
  containerSecurityContext:
    enabled: true
    runAsUser: 1001
    readOnlyRootFilesystem: false
volumePermissions:
  enabled: true
  image:
    registry: 732722833398.dkr.ecr.us-west-2.amazonaws.com
    repository: os-shell
    tag: 12-debian-12-r51
    pullPolicy: IfNotPresent
  resourcesPreset: "nano"
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
  depends_on = [module.rds, kubernetes_job_v1.adjust_rds_db]
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
  depends_on = [module.rds, kubernetes_job_v1.adjust_rds_db]
  connection {
    host     = module.rds[0].cluster_endpoint
    port     = 5432
    username = module.rds[0].cluster_master_username
    password = local.pg_password
  }
}

resource "postgresql_database" "eureka_kong" {
  depends_on = [postgresql_role.kong, kubernetes_job_v1.adjust_rds_db]
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
  depends_on = [postgresql_role.keycloak, kubernetes_job_v1.adjust_rds_db]
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
  depends_on = [helm_release.postgresql, module.rds]
  count      = var.pgadmin4 ? 1 : 0
  namespace  = rancher2_namespace.this.name
  repository = local.catalogs.runix
  name       = "pgadmin4"
  chart      = "pgadmin4"
  version    = "1.10.1"
  values = [<<-EOF
image:
  tag: 9.7.0
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

resource "rancher2_secret" "adjust_rds_db" {
  count        = var.setup_type == "full" && !var.pg_embedded ? 1 : 0
  name         = "adjust-rds-db"
  project_id   = rancher2_project.this.id
  namespace_id = rancher2_namespace.this.id
  data = {
    PGHOST     = base64encode(module.rds[0].cluster_endpoint)
    PGUSER     = base64encode(module.rds[0].cluster_master_username)
    PGPASSWORD = base64encode(var.pg_password)
  }
}


resource "kubernetes_job_v1" "adjust_rds_db" {
  count      = var.setup_type == "full" && !var.pg_embedded ? 1 : 0
  depends_on = [module.rds, rancher2_secret.db-credentials, rancher2_secret.adjust_rds_db]
  provider   = kubernetes
  metadata {
    generate_name = "adjust-rds-db-"
    namespace     = rancher2_namespace.this.name
    labels = {
      app = "adjust-rds-db"
    }
  }
  spec {
    template {
      metadata {
        labels = {
          app = "adjust-rds-db"
        }
      }
      spec {
        restart_policy = "OnFailure"
        container {
          name              = "adjust-rds-db"
          image             = "732722833398.dkr.ecr.us-west-2.amazonaws.com/adjust-rds-db:latest"
          image_pull_policy = "Always"
          env_from {
            secret_ref {
              name = rancher2_secret.adjust_rds_db[0].name
            }
          }
          env {
            name  = "DBS_2_DROP"
            value = "keycloak kong"
          }
          env {
            name  = "ROLES_2_DROP"
            value = "keycloak keycloak_admin kong kong_admin"
          }
        }
      }
    }
    backoff_limit = 3
  }
  wait_for_completion = true
  timeouts {
    create = "5m"
    update = "5m"
  }
}
