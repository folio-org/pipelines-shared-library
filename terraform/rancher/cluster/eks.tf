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
  testing_cluster = "folio-testing"
}

module "eks_cluster" {
  source  = "terraform-aws-modules/eks/aws"
  version = "~>19.12.0"

  cluster_name      = terraform.workspace
  cluster_version   = "1.28"
  cluster_ip_family = "ipv4"

  cluster_endpoint_private_access = false
  cluster_endpoint_public_access  = true

  vpc_id     = data.aws_vpc.this.id
  subnet_ids = data.aws_subnets.private.ids

  cluster_addons = {
    coredns = {
      most_recent = true
    }
    kube-proxy = {
      most_recent = true
    }
    vpc-cni = {
      most_recent = true
      #       before_compute           = true
      service_account_role_arn = module.vpc_cni_irsa_role.iam_role_arn
      #       configuration_values = jsonencode({
      #         env = {
      #           # Reference docs https://docs.aws.amazon.com/eks/latest/userguide/cni-increase-ip-addresses.html
      #           ENABLE_PREFIX_DELEGATION = "true"
      #           WARM_PREFIX_TARGET       = "1"
      #         }
      #       })
    }
    aws-ebs-csi-driver = {
      most_recent              = true
      service_account_role_arn = module.ebs_csi_irsa_role.iam_role_arn
    }
  }

  # Switch off cloudwatch log group
  create_cloudwatch_log_group = false
  cluster_enabled_log_types   = []
  # aws-auth configmap
  manage_aws_auth_configmap = true
  aws_auth_users            = local.admin_users_map

  cluster_security_group_additional_rules = {
    ingress_nodes_ephemeral_ports_tcp = {
      description                = "Nodes on ephemeral ports"
      protocol                   = "tcp"
      from_port                  = 1025
      to_port                    = 65535
      type                       = "ingress"
      source_node_security_group = true
    }

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
    ingress_self_all = {
      description = "Node to node all ports/protocols"
      protocol    = "-1"
      from_port   = 0
      to_port     = 0
      type        = "ingress"
      self        = true
    }
  }

  eks_managed_node_groups = merge({
    eks_node_group = {
      cluster_name = terraform.workspace
      name         = terraform.workspace
      description  = "EKS managed node group"
      ami_type     = "AL2_x86_64"

      capacity_type  = var.eks_nodes_type
      disk_size      = 50
      instance_types = var.asg_instance_types

      enable_monitoring = false

      min_size     = var.eks_nodes_group_size.min_size
      max_size     = var.eks_nodes_group_size.max_size
      desired_size = var.eks_nodes_group_size.min_size

      # For future schedule https://registry.terraform.io/modules/terraform-aws-modules/eks/aws/latest/submodules/eks-managed-node-group#input_schedules
    }
    },
    terraform.workspace == local.testing_cluster ? {
      eks_node_group_cicypress = {
        cluster_name = terraform.workspace
        name         = "cicypress"
        description  = "EKS managed node group for CI Cypress env"
        ami_type     = "AL2_x86_64"

        capacity_type  = var.eks_nodes_type
        disk_size      = 100
        instance_types = var.asg_instance_types

        enable_monitoring = false

        min_size     = 1
        max_size     = 4
        desired_size = 1

        taints = {
          qualitygate = {
            key    = "folio.org/qualitygate"
            value  = "cicypress"
            effect = "NO_SCHEDULE"
          }
        }

        labels = {
          "folio.org/qualitygate" = "cicypress"
        }

        tags = merge(
          var.tags,
          {
            kubernetes_cluster = terraform.workspace
        })

        # For future schedule https://registry.terraform.io/modules/terraform-aws-modules/eks/aws/latest/submodules/eks-managed-node-group#input_schedules
      },
      eks_node_group_cikarate = {
        cluster_name = terraform.workspace
        name         = "cikarate"
        description  = "EKS managed node group for CI Karate env"
        ami_type     = "AL2_x86_64"

        capacity_type  = var.eks_nodes_type
        disk_size      = 100
        instance_types = var.asg_instance_types

        enable_monitoring = false

        min_size     = 1
        max_size     = 4
        desired_size = 1

        taints = {
          qualitygate = {
            key    = "folio.org/qualitygate"
            value  = "cikarate"
            effect = "NO_SCHEDULE"
          }
        }

        labels = {
          "folio.org/qualitygate" = "cikarate"
        }

        tags = merge(
          var.tags,
          {
            kubernetes_cluster = terraform.workspace
        })

        # For future schedule https://registry.terraform.io/modules/terraform-aws-modules/eks/aws/latest/submodules/eks-managed-node-group#input_schedules
      }
    } : {}
  )

  tags = merge(
    var.tags,
    {
      kubernetes_cluster = terraform.workspace
  })
}
