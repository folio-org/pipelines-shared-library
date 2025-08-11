terraform {
  required_version = ">=1.6.1"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.5"
    }
    rancher2 = {
      source  = "rancher/rancher2"
      version = "~> 7.3"
    }
    helm = {
      source  = "hashicorp/helm"
      version = "~> 3.0"
    }
    kubernetes = {
      source  = "hashicorp/kubernetes"
      version = "~> 2.38"
    }
  }
}