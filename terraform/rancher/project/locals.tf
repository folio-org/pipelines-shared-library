# Creating local variables that are used in the rest of the terraform file.
locals {
  testing_cluster       = "folio-testing"
  opensearch_value      = try(nonsensitive(jsondecode(data.aws_ssm_parameter.opensearch[0].value)), "")
  msk_value             = try(nonsensitive(jsondecode(data.aws_ssm_parameter.msk[0].value)), "")
  env_name              = join("-", [data.rancher2_cluster.this.name, var.rancher_project_name])
  group_name            = join(".", [data.rancher2_cluster.this.name, var.rancher_project_name])
  okapi_url             = join(".", [join("-", [data.rancher2_cluster.this.name, var.rancher_project_name, "okapi"]), var.root_domain])
  minio_url             = join(".", [join("-", [data.rancher2_cluster.this.name, var.rancher_project_name, "minio"]), var.root_domain])
  minio_console_url     = join(".", [join("-", [data.rancher2_cluster.this.name, var.rancher_project_name, "minio-console"]), var.root_domain])
  db_snapshot_arn       = "arn:aws:rds:${var.aws_region}:${data.aws_caller_identity.current.account_id}:cluster-snapshot:${var.pg_rds_snapshot_name}"
  system_user_modules   = ["mod-data-export-spring", "mod-inn-reach", "mod-search", "mod-consortia", "mod-remote-storage", "mod-entities-links", "mod-erm-usage-harvester", "mod-pubsub", "mod-dcb"]
  s3_integrated_modules = ["mod-data-export", "mod-data-export-worker", "mod-data-import", "mod-lists", "mod-bulk-operations", "mod-oai-pmh"]
  s3_buckets_map = { for module in local.s3_integrated_modules :
    module => "${data.rancher2_cluster.this.name}-${var.rancher_project_name}-${replace(module, "mod-", "")}"
  }
  s3_buckets_string = join(",", values(local.s3_buckets_map))
}
