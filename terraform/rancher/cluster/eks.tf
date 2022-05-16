module "eks_cluster" {
  source  = "terraform-aws-modules/eks/aws"
  version = "18.7.2"

  cluster_name      = terraform.workspace
  cluster_version   = "1.21"
  cluster_ip_family = "ipv4"

  vpc_id     = var.vpc_create ? module.vpc[0].vpc_id : var.vpc_id
  subnet_ids = var.vpc_create ? module.vpc[0].private_subnets : data.aws_subnets.private[0].ids

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
    rancher_node_group = {
      name     = join("-", [terraform.workspace])
      ami_type = "AL2_x86_64"

      capacity_type  = var.eks_nodes_type
      disk_size      = 50
      instance_types = var.asg_instance_types

      min_size     = var.eks_node_group_size.min_size
      max_size     = var.eks_node_group_size.max_size
      desired_size = var.eks_node_group_size.desired_size

      update_config = {
        max_unavailable_percentage = 50
      }
    }
  }
  tags = var.tags
}
