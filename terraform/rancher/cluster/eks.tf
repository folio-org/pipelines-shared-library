data "aws_caller_identity" "current" {}

data "aws_vpc" "this" {
  filter {
    name   = "tag:Name"
    values = [var.vpc_name]
  }
}

data "aws_subnets" "private" {
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
  version = "18.26.6"

  cluster_name      = terraform.workspace
  cluster_version   = "1.21"
  cluster_ip_family = "ipv4"

  vpc_id     = data.aws_vpc.this.id
  subnet_ids = data.aws_subnets.private.ids

  cluster_addons = {
    coredns    = {}
    kube-proxy = {}
    vpc-cni    = {}
  }

  # Extend cluster security group rules
  cluster_security_group_additional_rules = {
    egress_nodes_ephemeral_ports_tcp = {
      description                = "To node 1025-65535"
      protocol                   = "tcp"
      from_port                  = 1025
      to_port                    = 65535
      type                       = "egress"
      source_node_security_group = true
    }
  }

  # Extend node-to-node security group rules
  node_security_group_additional_rules = {
    ingress_allow_access_from_control_plane = {
      type                          = "ingress"
      protocol                      = "tcp"
      from_port                     = 9443
      to_port                       = 9443
      source_cluster_security_group = true
      description                   = "Allow access from control plane to webhook port of AWS load balancer controller"
    }
    ingress_self_all = {
      type        = "ingress"
      protocol    = "-1"
      from_port   = 0
      to_port     = 0
      self        = true
      description = "Node to node all ports/protocols"
    }
    egress_all = {
      type        = "egress"
      protocol    = "-1"
      from_port   = 0
      to_port     = 0
      cidr_blocks = ["0.0.0.0/0"]
      description = "Node all egress"
    }
  }

  eks_managed_node_groups = {
    eks_node_group = {
      name     = terraform.workspace
      ami_type = "AL2_x86_64"

      capacity_type  = var.eks_nodes_type
      disk_size      = 50
      instance_types = var.asg_instance_types

      min_size     = var.eks_node_group_size.min_size
      max_size     = var.eks_node_group_size.max_size
      desired_size = var.eks_node_group_size.desired_size

      update_config = {
        max_unavailable_percentage = 75
      }
    }
  }

  # aws-auth configmap
  manage_aws_auth_configmap = true

  aws_auth_users = local.admin_users_map

  tags = merge(
    var.tags,
    {
      Region = var.aws_region
      Env    = terraform.workspace
  })
}
