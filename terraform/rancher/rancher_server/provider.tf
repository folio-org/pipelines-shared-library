data "aws_eks_cluster" "cluster" {
  name = var.rancher_cluster_name
}

data "aws_eks_cluster_auth" "cluster" {
  name = var.rancher_cluster_name
}

provider "aws" {
  region = var.aws_region
}

provider "rancher2" {
  api_url   = "https://${var.rancher_hostname}"
  token_key = var.rancher_token_key
  insecure  = true
}

provider "kubernetes" {
  host                   = data.aws_eks_cluster.cluster.endpoint
  cluster_ca_certificate = base64decode(data.aws_eks_cluster.cluster.certificate_authority.0.data)
  token                  = data.aws_eks_cluster_auth.cluster.token
}

provider "helm" {
  #  alias = "cluster"
  kubernetes {
    host                   = data.aws_eks_cluster.cluster.endpoint
    cluster_ca_certificate = base64decode(data.aws_eks_cluster.cluster.certificate_authority.0.data)
    token                  = data.aws_eks_cluster_auth.cluster.token
  }
}
