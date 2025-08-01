terraform {
  required_version = ">=1.6.1"
  required_providers {
    helm = {
      source  = "hashicorp/helm"
      version = "3.0.2"
    }
    aws = {
      source  = "hashicorp/aws"
      version = "6.5.0"
    }
    rancher2 = {
      source  = "rancher/rancher2"
      version = "7.3.2"
    }
  }
}