terraform {
  required_version = ">=1.0.0"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~>4.62"
    }
    rancher2 = {
      source  = "rancher/rancher2"
      version = "3.2.0"
    }
    random = {
      source  = "hashicorp/random"
      version = "3.3.2"
    }
    kubectl = {
      source  = "gavinbunney/kubectl"
      version = "1.14.0"
    }
  }
}
