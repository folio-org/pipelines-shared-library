#AWS Load Balancer controller Helm chart for Kubernetes
resource "helm_release" "alb_controller" {
  depends_on = [module.eks_cluster, module.load_balancer_controller_irsa_role]
  name       = "aws-load-balancer-controller"
  namespace  = "kube-system"
  repository = "https://aws.github.io/eks-charts"
  chart      = "aws-load-balancer-controller"
  version    = "1.4.8"
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
    value = terraform.workspace
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

##CSI interface used by Container Orchestrators to manage the lifecycle of Amazon EBS volumes.
#resource "helm_release" "aws_ebs_csi" {
#  depends_on = [module.eks_cluster, module.ebs_csi_irsa_role]
#  name       = "aws-ebs-csi-driver"
#  namespace  = "kube-system"
#  repository = "https://kubernetes-sigs.github.io/aws-ebs-csi-driver"
#  chart      = "aws-ebs-csi-driver"
#  version    = "2.12.1"
#  set {
#    name  = "controller.region"
#    value = var.aws_region
#  }
#  set {
#    name  = "controller.serviceAccount.name"
#    value = "aws-ebs-csi-driver"
#  }
#  set {
#    name  = "controller.serviceAccount.annotations.eks\\.amazonaws\\.com/role-arn"
#    value = module.ebs_csi_irsa_role.iam_role_arn
#  }
#}
#
#Add External DNS
resource "helm_release" "external_dns" {
  depends_on = [module.eks_cluster, module.external_dns_irsa_role]
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
    value = terraform.workspace
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
  depends_on = [module.eks_cluster, module.cluster_autoscaler_role]
  name       = "aws-cluster-autoscaler"
  namespace  = "kube-system"
  repository = "https://kubernetes.github.io/autoscaler"
  chart      = "cluster-autoscaler"
  version    = "9.27.0"
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
    value = terraform.workspace
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
    name  = "rbac.create"
    value = true
  }
  set {
    name  = "rbac.serviceAccount.annotations.eks\\.amazonaws\\.com/role-arn"
    value = module.cluster_autoscaler_role.iam_role_arn
  }
}
