# Getting the cluster data from the variable rancher_cluster_name.
data "rancher2_cluster" "this" {
  name = var.rancher_cluster_name
}

# Getting the EKS cluster data from the Rancher cluster name.
data "aws_eks_cluster" "this" {
  name = data.rancher2_cluster.this.name
}

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
