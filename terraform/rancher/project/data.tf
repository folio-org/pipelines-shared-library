data "rancher2_cluster" "this" {
  name = var.rancher_cluster_name
}

data "aws_eks_cluster" "this" {
  name = data.rancher2_cluster.this.name
}

# Used for accesing Account ID and ARN
data "aws_caller_identity" "current" {}

data "aws_subnets" "private" {
  filter {
    name   = "vpc-id"
    values = [data.aws_eks_cluster.this.vpc_config[0].vpc_id]
  }

  tags = {
    Type = "private"
  }
}

data "aws_subnets" "database" {
  filter {
    name   = "vpc-id"
    values = [data.aws_eks_cluster.this.vpc_config[0].vpc_id]
  }

  tags = {
    Type = "database"
  }
}

data "http" "install" {
  url = local.install_json_url
  request_headers = {
    Accept = "application/json"
  }
}

data "aws_s3_object" "saved_to_s3_install_json" {
  count  = var.restore_from_saved_s3_install_json ? 1 : 0
  bucket = trimprefix(var.s3_postgres-backups-bucket-name, "s3://")
  key    = join("", [trimsuffix(var.path_of_postgresql_backup, ".psql"), "_install.json"])
}

data "aws_s3_object" "saved_to_s3_okapi_install_json" {
  count  = var.restore_from_saved_s3_install_json ? 1 : 0
  bucket = trimprefix(var.s3_postgres-backups-bucket-name, "s3://")
  key    = join("", [trimsuffix(var.path_of_postgresql_backup, ".psql"), "_okapi_install.json"])
}


locals {
  env_name          = join("-", [data.rancher2_cluster.this.name, var.rancher_project_name])
  group_name        = join(".", [data.rancher2_cluster.this.name, var.rancher_project_name])
  frontend_url      = join(".", [join("-", [data.rancher2_cluster.this.name, var.rancher_project_name]), var.root_domain])
  okapi_url         = join(".", [join("-", [data.rancher2_cluster.this.name, var.rancher_project_name, "okapi"]), var.root_domain])
  minio_url         = join(".", [join("-", [data.rancher2_cluster.this.name, var.rancher_project_name, "minio"]), var.root_domain])
  minio_console_url = join(".", [join("-", [data.rancher2_cluster.this.name, var.rancher_project_name, "minio-console"]), var.root_domain])
  github_url        = "https://raw.githubusercontent.com/folio-org"
  install_json_url  = join("/", [local.github_url, var.repository, var.branch, "install.json"])

  helm_configs = jsondecode(file("${path.module}/resources/helm/${var.env_config}.json"))

  custom_s3_install_json = var.restore_from_saved_s3_install_json ? data.aws_s3_object.saved_to_s3_install_json[0].body : ""

  modules_to_install = var.install_json != "" ? var.install_json : local.custom_s3_install_json

  modules_list = local.modules_to_install != "" ? jsondecode(local.modules_to_install)[*]["id"] : jsondecode(data.http.install.body)[*]["id"]

  folio_helm_repo_name = "folio-helm"

  db_snapshot_arn = "arn:aws:rds:${var.aws_region}:${data.aws_caller_identity.current.account_id}:cluster-snapshot:${var.pg_rds_snapshot_name}"

  modules_map = {
    for id in local.modules_list : regex("^(.*)-(\\d*\\.\\d*\\.\\d*.*)$", id)[0] => regex("^(.*)-(\\d*\\.\\d*\\.\\d*.*)$", id)[1]
  }
  backend_map = {
    for k, v in local.modules_map : k => v if substr(k, 0, length("mod-")) == "mod-"
  }
  edge-map = {
    for k, v in local.modules_map : k => v if substr(k, 0, length("edge-")) == "edge-" && !contains([k], "edge-sip2")
  }
  edge-sip2-map = {
    for k, v in local.modules_map : k => v if contains([k], "edge-sip2")
  }

  edge_ephemeral_config = {
    "edge-rtac" = {},
    "edge-oai-pmh" = {
      "test_oaipmh" = "test-user,test"
    },
    "edge-patron" = {},
    "edge-orders" = {
      "test_edge_orders" = "test-user,test",
    }
    "edge-ncip" = {}
    "edge-dematic" = {
      (var.tenant_id) = "stagingDirector,${var.tenant_id}"
    },
    "edge-caiasoft" = {
      (var.tenant_id) = "caiaSoftClient,${var.tenant_id}"
    },
    "edge-connexion" = {}
  }

  edge_ephemeral_properties = [
    for k, v in local.edge_ephemeral_config : v if contains(keys(local.edge-map), k)
  ]
}
