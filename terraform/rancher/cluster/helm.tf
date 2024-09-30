#AWS Load Balancer controller Helm chart for Kubernetes
resource "helm_release" "alb_controller" {
  name       = "aws-load-balancer-controller"
  namespace  = "kube-system"
  repository = "https://aws.github.io/eks-charts"
  chart      = "aws-load-balancer-controller"
  version    = "1.8.3"
  set {
    name  = "region"
    value = var.aws_region
  }
  set {
    name  = "vpcId"
    value = data.aws_vpc.this.id
  }
  set {
    name  = "clusterName"
    value = module.eks_cluster.cluster_name
  }
  set {
    name  = "rbac.create"
    value = true
  }
  set {
    name  = "serviceAccount.create"
    value = true
  }
  set {
    name  = "serviceAccount.name"
    value = "aws-load-balancer-controller"
  }
  set {
    name  = "serviceAccount.annotations.eks\\.amazonaws\\.com/role-arn"
    value = module.load_balancer_controller_irsa_role.iam_role_arn
  }
}

#Add External DNS
resource "helm_release" "external_dns" {
  name       = "external-dns"
  namespace  = "kube-system"
  repository = "https://charts.bitnami.com/bitnami"
  chart      = "external-dns"
  version    = "6.16.0"
  set {
    name  = "provider"
    value = "aws"
  }
  set {
    name  = "aws.region"
    value = var.aws_region
  }
  set {
    name  = "domainFilters[0]"
    value = var.root_domain
  }
  set {
    name  = "txtOwnerId"
    value = module.eks_cluster.cluster_name
  }
  set {
    name  = "rbac.create"
    value = true
  }
  set {
    name  = "serviceAccount.create"
    value = true
  }
  set {
    name  = "serviceAccount.name"
    value = "external-dns"
  }
  set {
    name  = "serviceAccount.annotations.eks\\.amazonaws\\.com/role-arn"
    value = module.external_dns_irsa_role.iam_role_arn
  }
}

resource "helm_release" "aws_cluster_autoscaler" {
  name       = "aws-cluster-autoscaler"
  namespace  = "kube-system"
  repository = "https://kubernetes.github.io/autoscaler"
  chart      = "cluster-autoscaler"
  # https://github.com/kubernetes/autoscaler/tree/master/cluster-autoscaler#releases
  # https://artifacthub.io/packages/helm/cluster-autoscaler/cluster-autoscaler
  version = "9.24.0"
  set {
    name  = "cloudProvider"
    value = "aws"
  }
  set {
    name  = "awsRegion"
    value = var.aws_region
  }
  set {
    name  = "autoDiscovery.clusterName"
    value = module.eks_cluster.cluster_name
  }
  set {
    name  = "extraArgs.balance-similar-node-groups"
    value = true
  }
  set {
    name  = "extraArgs.scale-down-enabled"
    value = true
  }
  set {
    name  = "extraArgs.expander"
    value = "least-waste"
  }
  set {
    name  = "rbac.create"
    value = true
  }
  set {
    name  = "rbac.serviceAccount.annotations.eks\\.amazonaws\\.com/role-arn"
    value = module.cluster_autoscaler_role.iam_role_arn
  }
}
