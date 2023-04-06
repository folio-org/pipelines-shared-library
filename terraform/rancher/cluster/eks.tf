data "aws_caller_identity" "current" {}

data "aws_vpc" "this" {
  filter {
    name   = "tag:Name"
    values = [var.vpc_name]
  }
}

data "aws_subnets" "private" {
  filter {
    name   = "availability-zone"
    values = ["${var.aws_region}a", "${var.aws_region}b"]
  }
  filter {
    name   = "vpc-id"
    values = [data.aws_vpc.this.id]
  }
  tags = {
    Type : "private"
  }
}

locals {
  admin_users_map = [
    for user in split(",", var.admin_users) : {
      userarn  = "arn:aws:iam::${data.aws_caller_identity.current.account_id}:user/${user}"
      username = user
      groups   = ["system:masters"]
    }
  ]
}

module "eks_cluster" {
  source  = "terraform-aws-modules/eks/aws"
  version = "19.12.0"
  # Switch off cloudwatch log group
  create_cloudwatch_log_group = false
  cluster_enabled_log_types = []

  cluster_name      = terraform.workspace
  cluster_version   = "1.23" //Highest version 1.24 or 1.25 need to be tested before applying
  cluster_ip_family = "ipv4"

  vpc_id     = data.aws_vpc.this.id
  subnet_ids = data.aws_subnets.private.ids

  cluster_addons = {
    preserve    = true
    most_recent = true
    timeouts = {
      create = "25m"
      delete = "10m"
    }
    coredns            = {}
    kube-proxy         = {}
    vpc-cni            = {}
    aws-ebs-csi-driver = {}
  }

  eks_managed_node_groups = {
    eks_node_group = {
      name        = terraform.workspace
      description = "EKS managed node group"
      ami_type    = "AL2_x86_64"

      capacity_type  = var.eks_nodes_type
      disk_size      = 50
      instance_types = var.asg_instance_types

      enable_monitoring = false

      min_size     = var.eks_nodes_group_size.min_size
      max_size     = var.eks_nodes_group_size.max_size
      desired_size = var.eks_nodes_group_size.min_size

      # For future schedule https://registry.terraform.io/modules/terraform-aws-modules/eks/aws/latest/submodules/eks-managed-node-group#input_schedules
    }
  }

  # aws-auth configmap
  manage_aws_auth_configmap = true

  aws_auth_users = local.admin_users_map

  tags = merge(
    var.tags,
    {
      kubernetes_cluster = terraform.workspace
  })
}
