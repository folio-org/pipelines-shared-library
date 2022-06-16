terraform {
  required_providers {
    kubectl = {
      source = "gavinbunney/kubectl"
    }

    rancher2 = {
      source  = "rancher/rancher2"
      version = "1.24.0"
    }
  }
}
data "aws_eks_cluster" "cluster" {
  name = "rancher"
}

data "aws_eks_cluster_auth" "cluster" {
  name = "rancher"
}

provider "rancher2" {

  api_url = "https://rancher.ci.folio.org/v3"
  # bootstrap = true
  token_key = "token-89xnj:9fvk2j8rkmx6mlwwzhzzn96phcf4pssnwszb9kcldzbzxwjgr24zpv"

}

provider "aws" {
  region = "us-west-2"
}


provider "kubernetes" {
  host                   = data.aws_eks_cluster.cluster.endpoint
  cluster_ca_certificate = base64decode(data.aws_eks_cluster.cluster.certificate_authority.0.data)
  token                  = data.aws_eks_cluster_auth.cluster.token
}

provider "helm" {
  alias = "cluster"
  kubernetes {
    host                   = data.aws_eks_cluster.cluster.endpoint
    cluster_ca_certificate = base64decode(data.aws_eks_cluster.cluster.certificate_authority.0.data)
    token                  = data.aws_eks_cluster_auth.cluster.token
  }
}

provider "kubectl" {
  host                   = data.aws_eks_cluster.cluster.endpoint
  cluster_ca_certificate = base64decode(data.aws_eks_cluster.cluster.certificate_authority.0.data)
  token                  = data.aws_eks_cluster_auth.cluster.token
  load_config_file       = false
}
