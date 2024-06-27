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

provider "helm" {
  kubernetes {
    host                   = data.aws_eks_cluster.this.endpoint
    cluster_ca_certificate = base64decode(data.aws_eks_cluster.this.certificate_authority.0.data)

    exec {
      api_version = "client.authentication.k8s.io/v1"
      command     = "aws"
      # This requires the awscli to be installed locally where Terraform is executed
      args = ["eks", "get-token", "--cluster-name", data.aws_eks_cluster.this.id]
    }
  }
}

provider "postgresql" {
  host            = var.pg_embedded ? local.pg_service_writer : module.rds[0].cluster_endpoint
  username        = var.pg_embedded ? var.pg_username : module.rds[0].cluster_master_username
  password        = var.pg_password == "" ? random_password.pg_password.result : var.pg_password
  port            = 5432
  database        = "postgres"
  sslmode         = "disable"
  connect_timeout = 30
}

provider "kubernetes" {
  host                   = data.aws_eks_cluster.this.endpoint
  cluster_ca_certificate = base64decode(data.aws_eks_cluster.this.certificate_authority.0.data)

  exec {
    api_version = "client.authentication.k8s.io/v1"
    command     = "aws"
    # This requires the awscli to be installed locally where Terraform is executed
    args = ["eks", "get-token", "--cluster-name", data.aws_eks_cluster.this.id]
  }
}
