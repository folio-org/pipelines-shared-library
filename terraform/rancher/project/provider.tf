provider "aws" {
  region = var.aws_region
}

provider "rancher2" {
  api_url   = var.rancher_server_url
  token_key = var.rancher_token_key
}


provider "kubectl" {
  apply_retry_count      = 5
  host                   = data.aws_eks_cluster.this.endpoint
  cluster_ca_certificate = base64decode(data.aws_eks_cluster.this.certificate_authority.0.data)
  load_config_file       = false

  exec {
    api_version = "client.authentication.k8s.io/v1beta1"
    command     = "aws"
    # This requires the awscli to be installed locally where Terraform is executed
    args = ["eks", "get-token", "--cluster-name", data.aws_eks_cluster.this.id]
  }
}

#provider "kubectl" {
#  host                   = data.aws_eks_cluster.this.endpoint
#  cluster_ca_certificate = base64decode(data.aws_eks_cluster.this.certificate_authority.0.data)
#  token                  = data.aws_eks_cluster_auth.this.token
#  load_config_file       = false
#}
