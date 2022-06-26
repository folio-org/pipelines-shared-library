# install rancher
resource "helm_release" "rancher" {
  provider         = helm.cluster
  name             = var.rancher_cluster_name
  repository       = "https://releases.rancher.com/server-charts/stable"
  chart            = "rancher"
  version          = var.rancher_version
  namespace        = "cattle-system"
  create_namespace = true

  set {
    name  = "hostname"
    value = var.rancher_hostname
  }

  set {
    name  = "ingress.extraAnnotations.alb\\.ingress\\.kubernetes\\.io/scheme"
    value = "internet-facing"
  }

  set {
    name  = "ingress.extraAnnotations.alb\\.ingress\\.kubernetes\\.io/success-codes"
    value = "200\\,404\\,301\\,302"
  }

  set {
    name  = "ingress.extraAnnotations.alb\\.ingress\\.kubernetes\\.io/listen-ports"
    value = "[{\"HTTP\": 80}\\,{\"HTTPS\":443}]"
  }

  set {
    name  = "ingress.extraAnnotations.alb\\.ingress\\.kubernetes\\.io/certificate-arn"
    value = "arn:aws:acm:us-west-2:732722833398:certificate/b1e1ca4b-0f0a-41c8-baaa-8b64a1cd4e0a"
  }

  set {
    name  = "ingress.extraAnnotations.kubernetes\\.io/ingress\\.class"
    value = "alb"
  }

  set {
    name  = "replicas"
    value = 3
  }
  set {
    name  = "tls"
    value = "external"
  }
}

# patches rancher service from rancher helm chart to NodePort type
resource "null_resource" "patch" {
  depends_on = [helm_release.rancher]
  triggers = {
    host                   = data.aws_eks_cluster.cluster.endpoint
    cluster_ca_certificate = base64decode(data.aws_eks_cluster.cluster.certificate_authority.0.data)
    token                  = data.aws_eks_cluster_auth.cluster.token

    cmd_patch = <<-EOT
      kubectl -n cattle-system patch svc rancher -p '{"spec": {"type": "NodePort"}}'
      kubectl -n cattle-system exec $(kubectl -n cattle-system get pods -l app=rancher | grep '1/1' | head -1 | awk '{ print $1 }') -- reset-password    
    EOT
  }

  provisioner "local-exec" {
    interpreter = ["/bin/bash", "-c"]
    environment = {
    }
    command = self.triggers.cmd_patch
  }
}

