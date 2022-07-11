# install rancher
resource "helm_release" "rancher" {
  name             = "rancher"
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
      kubectl -n cattle-system patch svc rancher -p '{"spec": {"type": "NodePort"}}' --kubeconfig $KUBECONFIG
      kubectl -n cattle-system exec $(kubectl -n cattle-system get pods -l app=rancher --kubeconfig $KUBECONFIG | grep '1/1' | head -1 | awk '{ print $1 }') --kubeconfig $KUBECONFIG -- reset-password
    EOT
  }
  provisioner "local-exec" {
    interpreter = ["/bin/bash", "-c"]
    environment = {
      KUBECONFIG = local_file.kube_cluster_yaml.filename
    }
    command = self.triggers.cmd_patch
  }
}

locals {
  kube_config = <<-EOT
    ---
    apiVersion: v1
    kind: Config
    clusters:
      - cluster:
          api-version: v1
          certificate-authority-data: ${data.aws_eks_cluster.cluster.certificate_authority.0.data}
          server: ${data.aws_eks_cluster.cluster.endpoint}
          # insecure-skip-tls-verify: true
        name: "rancher-management"
    contexts:
      - context:
          cluster: "rancher-management"
          user: "kube-admin-rancher-management"
        name: "rancher-management"
    current-context: "rancher-management"
    users:
      - name: "kube-admin-rancher-management"
        user:
          token: ${data.aws_eks_cluster_auth.cluster.token}
  EOT
}

resource "local_file" "kube_cluster_yaml" {
  filename = "${path.root}/outputs/kube_config_cluster.yml"
  content  = local.kube_config
}

resource "null_resource" "wait_for_rancher" {
  provisioner "local-exec" {
    command = <<EOF
      while [ "$${resp}" != "pong" ]; do
          resp=$(curl -sSk -m 2 "https://$${RANCHER_HOSTNAME}/ping")
          echo "Rancher Response: $${resp}"
          if [ "$${resp}" != "pong" ]; then
            sleep 10
          fi
      done
    EOF
    environment = {
      RANCHER_HOSTNAME = var.rancher_hostname
      TF_LINK          = helm_release.rancher.name
    }
  }
}
