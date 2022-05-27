data "rancher2_cluster" "cluster" {
  name = var.rancher_cluster_name
}

data "aws_eks_cluster" "cluster" {
  name = data.rancher2_cluster.cluster.name
}

#TODO Add tag for selection of db subnets during creation
data "aws_subnets" "db_subnet" {
  filter {
    name   = "vpc-id"
    values = [data.aws_eks_cluster.cluster.vpc_config[0].vpc_id]
  }
  filter {
    name   = "tag:Name"
    values = ["perf-eks-vpc-bulk-edit-db-us-west-2a", "perf-eks-vpc-bulk-edit-db-us-west-2b", "perf-eks-vpc-bulk-edit-db-us-west-2c", "perf-eks-vpc-bulk-edit-db-us-west-2d"]
  }
}

data "tfvars_file" "env_type" {
  filename = "env_type.tfvars"
}
