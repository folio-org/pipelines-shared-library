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
  name = var.rancher_cluster_name
}

data "aws_eks_cluster_auth" "cluster" {
  name = var.rancher_cluster_name
}

provider "rancher2" {

  api_url = "https://rancher.ci.folio.org/v3"
  # bootstrap = true
  token_key = var.rancher_token_key

}

provider "aws" {
  region = var.aws_region
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
