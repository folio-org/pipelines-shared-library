terraform {
  required_version = ">=1.0.0"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~>4.62.0"
    }
    kubernetes = {
      source  = "hashicorp/kubernetes"
      version = "~>2.19.0"
    }
    rancher2 = {
      source  = "rancher/rancher2"
      version = "~>1.25.0"
    }
    helm = {
      source  = "hashicorp/helm"
      version = "~>2.9.0"
    }
  }
}
