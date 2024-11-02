# Creating local variables that are used in the rest of the terraform file.
locals {
  testing_cluster       = "folio-testing"
  opensearch_value      = try(nonsensitive(jsondecode(data.aws_ssm_parameter.opensearch[0].value)), "")
  msk_value             = try(nonsensitive(jsondecode(data.aws_ssm_parameter.msk[0].value)), "")
  env_name              = join("-", [data.rancher2_cluster.this.name, var.rancher_project_name])
  group_name            = join(".", [data.rancher2_cluster.this.name, var.rancher_project_name])
  okapi_url             = join(".", [join("-", [data.rancher2_cluster.this.name, var.rancher_project_name, "okapi"]), var.root_domain])
  minio_url             = join(".", [join("-", [data.rancher2_cluster.this.name, var.rancher_project_name, "minio"]), var.root_domain])
  kong_url              = join(".", [join("-", [data.rancher2_cluster.this.name, var.rancher_project_name, "kong"]), var.root_domain])
  keycloak_url          = join(".", [join("-", [data.rancher2_cluster.this.name, var.rancher_project_name, "keycloak"]), var.root_domain])
  minio_console_url     = join(".", [join("-", [data.rancher2_cluster.this.name, var.rancher_project_name, "minio-console"]), var.root_domain])
  db_snapshot_arn       = "arn:aws:rds:${var.aws_region}:${data.aws_caller_identity.current.account_id}:cluster-snapshot:${var.pg_rds_snapshot_name}"

  system_user_modules = ["mod-data-export-spring", "mod-inn-reach", "mod-search", "mod-consortia",
    "mod-remote-storage", "mod-entities-links", "mod-erm-usage-harvester", "mod-pubsub", "mod-dcb", "mod-lists",
    "mod-linked-data", "mod-tlr", "mod-marc-migrations", "mod-requests-mediated", "mod-consortia-keycloak",
    "mod-scheduler", "mod-users-keycloak", "mod-roles-keycloak"
  ]

  s3_integrated_modules = ["mod-data-export", "mod-data-export-worker", "mod-data-import", "mod-lists",
    "mod-bulk-operations", "mod-oai-pmh", "mod-marc-migrations"
  ]

  s3_buckets_map = { for module in local.s3_integrated_modules :
    module => "${data.rancher2_cluster.this.name}-${var.rancher_project_name}-${replace(module, "mod-", "")}"
  }
  s3_buckets_string   = join(",", values(local.s3_buckets_map))
  ssm_params          = ["master_folio-backend-admin-client", "master_mgr-applications", "master_mgr-tenant-entitlements", "master_mgr-tenants"]
  schedule_namespaces = ["cicypress", "cikarate"]
  schedule_object     = <<-EOF
nodeSelector:
  "folio.org/qualitygate": ${var.rancher_project_name}
tolerations:
- key : "folio.org/qualitygate"
  operator : "Equal"
  value : ${var.rancher_project_name}
  effect : "NoSchedule"
EOF

  schedule_value = contains(local.schedule_namespaces, var.rancher_project_name) ? local.schedule_object : ""

  catalogs = {
    bitnami    = "https://repository.folio.org/repository/helm-bitnami-proxy",
    provectus  = "https://provectus.github.io/kafka-ui-charts",
    opensearch = "https://opensearch-project.github.io/helm-charts",
    runix      = "https://helm.runix.net"
  }
}
