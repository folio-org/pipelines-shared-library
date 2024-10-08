module "load_balancer_controller_irsa_role" {
  source  = "terraform-aws-modules/iam/aws//modules/iam-role-for-service-accounts-eks"
  version = "~>5.44.0"

  role_name                              = join("-", [terraform.workspace, "load-balancer-controller-role"])
  attach_load_balancer_controller_policy = true

  oidc_providers = {
    main = {
      provider_arn               = module.eks_cluster.oidc_provider_arn
      namespace_service_accounts = ["kube-system:aws-load-balancer-controller"]
    }
  }

  tags = merge(
    {
      Name = "load-balancer-controller-role"
    },
    var.tags
  )
}

module "vpc_cni_irsa_role" {
  source  = "terraform-aws-modules/iam/aws//modules/iam-role-for-service-accounts-eks"
  version = "~>5.16.0"

  role_name             = join("-", [terraform.workspace, "vpc-cni-role"])
  attach_vpc_cni_policy = true
  vpc_cni_enable_ipv4   = true

  oidc_providers = {
    ex = {
      provider_arn               = module.eks_cluster.oidc_provider_arn
      namespace_service_accounts = ["kube-system:aws-node"]
    }
  }

  tags = merge(
    {
      Name = "vpc-cni-role"
    },
    var.tags
  )
}

module "ebs_csi_irsa_role" {
  source  = "terraform-aws-modules/iam/aws//modules/iam-role-for-service-accounts-eks"
  version = "~>5.16.0"

  role_name             = join("-", [terraform.workspace, "ebs-csi-role"])
  attach_ebs_csi_policy = true

  oidc_providers = {
    main = {
      provider_arn               = module.eks_cluster.oidc_provider_arn
      namespace_service_accounts = ["kube-system:ebs-csi-controller-sa"]
    }
  }

  tags = merge(
    {
      Name = "ebs-csi-role"
    },
    var.tags
  )
}

module "external_dns_irsa_role" {
  source  = "terraform-aws-modules/iam/aws//modules/iam-role-for-service-accounts-eks"
  version = "~>5.16.0"

  role_name                     = join("-", [terraform.workspace, "external-dns-role"])
  attach_external_dns_policy    = true
  external_dns_hosted_zone_arns = ["arn:aws:route53:::hostedzone/*"]

  oidc_providers = {
    main = {
      provider_arn               = module.eks_cluster.oidc_provider_arn
      namespace_service_accounts = ["kube-system:external-dns"]
    }
  }

  tags = merge(
    {
      Name = "external-dns-role"
    },
    var.tags
  )
}

module "cluster_autoscaler_role" {
  source  = "terraform-aws-modules/iam/aws//modules/iam-role-for-service-accounts-eks"
  version = "~>5.16.0"

  role_name                        = join("-", [terraform.workspace, "cluster-autoscaler"])
  attach_cluster_autoscaler_policy = true
  cluster_autoscaler_cluster_ids   = [module.eks_cluster.cluster_name]

  oidc_providers = {
    main = {
      provider_arn               = module.eks_cluster.oidc_provider_arn
      namespace_service_accounts = ["kube-system:aws-cluster-autoscaler"]
    }
  }

  tags = merge(
    {
      Name = "cluster-autoscaler"
    },
    var.tags
  )
}
