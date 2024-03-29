# Getting the cluster data from the variable rancher_cluster_name.
data "rancher2_cluster" "this" {
  name = var.rancher_cluster_name
}

# Getting the EKS cluster data from the Rancher cluster name.
data "aws_eks_cluster" "this" {
  name = data.rancher2_cluster.this.name
}

#data "aws_eks_cluster_auth" "this" {
#  name = data.rancher2_cluster.this.name
#}

# Used for accessing Account ID and ARN
data "aws_caller_identity" "current" {}

# Getting the subnets that are tagged with "private" and are in the VPC that the EKS cluster is in.
data "aws_subnets" "private" {
  filter {
    name   = "availability-zone"
    values = ["${var.aws_region}a", "${var.aws_region}b"]
  }
  filter {
    name   = "vpc-id"
    values = [data.aws_eks_cluster.this.vpc_config[0].vpc_id]
  }
  tags = {
    Type = "private"
  }
}

# Getting the subnets that are tagged with "database" and are in the VPC that the EKS cluster is in.
data "aws_subnets" "database" {
  filter {
    name   = "availability-zone"
    values = ["${var.aws_region}a", "${var.aws_region}b"]
  }
  filter {
    name   = "vpc-id"
    values = [data.aws_eks_cluster.this.vpc_config[0].vpc_id]
  }
  tags = {
    Type = "database"
  }
}

data "aws_ssm_parameter" "opensearch" {
  count = var.opensearch_shared ? 1 : 0
  name  = var.opensearch_shared_name
}

data "aws_ssm_parameter" "msk" {
  count = var.kafka_shared ? 1 : 0
  name  = var.kafka_shared_name
}

# Creating local variables that are used in the rest of the terraform file.
locals {
  opensearch_value  = try(nonsensitive(jsondecode(data.aws_ssm_parameter.opensearch[0].value)), "")
  msk_value         = try(nonsensitive(jsondecode(data.aws_ssm_parameter.msk[0].value)), "")
  env_name          = join("-", [data.rancher2_cluster.this.name, var.rancher_project_name])
  group_name        = join(".", [data.rancher2_cluster.this.name, var.rancher_project_name])
  okapi_url         = join(".", [join("-", [data.rancher2_cluster.this.name, var.rancher_project_name, "okapi"]), var.root_domain])
  minio_url         = join(".", [join("-", [data.rancher2_cluster.this.name, var.rancher_project_name, "minio"]), var.root_domain])
  minio_console_url = join(".", [join("-", [data.rancher2_cluster.this.name, var.rancher_project_name, "minio-console"]), var.root_domain])
  db_snapshot_arn   = "arn:aws:rds:${var.aws_region}:${data.aws_caller_identity.current.account_id}:cluster-snapshot:${var.pg_rds_snapshot_name}"
}
