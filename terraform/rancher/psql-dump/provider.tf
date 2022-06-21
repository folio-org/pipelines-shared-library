provider "aws" {
  region = var.aws_region
}

provider "rancher2" {
  api_url   = var.rancher_server_url
  token_key = var.rancher_token_key
}

data "aws_eks_cluster" "cluster" {
  name = var.rancher_cluster_name
}

data "aws_eks_cluster_auth" "cluster" {
  name = var.rancher_cluster_name
}

/*data "terraform_remote_state" "project_state" {
  backend = "s3"
  config = {
    bucket = "folio-terraform"
    workspace_key_prefix = "rancher-project/${var.rancher_project_name}"
    key    = "terraform.tfstate"
    region = "us-east-1"
  }
}*/

provider "helm" {
  kubernetes {
    host                   = data.aws_eks_cluster.cluster.endpoint
    cluster_ca_certificate = base64decode(data.aws_eks_cluster.cluster.certificate_authority.0.data)
    token                  = data.aws_eks_cluster_auth.cluster.token
  }
}
