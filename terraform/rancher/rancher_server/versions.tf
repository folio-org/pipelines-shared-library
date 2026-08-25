terraform {
  required_version = ">=1.6.1"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~>5.34" # aligned with cluster/ module
    }
    kubernetes = {
      source  = "hashicorp/kubernetes"
      version = "~>2.23" # aligned with cluster/ module
    }
    rancher2 = {
      source  = "rancher/rancher2"
      version = "4.1.0" # supports Rancher 2.8+; aligned with cluster/ module
    }
    helm = {
      source  = "hashicorp/helm"
      version = "~>2.11" # aligned with cluster/ module
    }
  }
}
