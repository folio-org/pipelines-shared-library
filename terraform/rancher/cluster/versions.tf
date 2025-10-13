terraform {
  required_version = ">=1.6.1"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~>5.34"
    }
    kubernetes = {
      source  = "hashicorp/kubernetes"
      version = "~>2.23"
    }
    helm = {
      source  = "hashicorp/helm"
      version = "~>2.11"
    }
    elasticstack = {
      source  = "elastic/elasticstack"
      version = "~>0.3.3"
    }
    rancher2 = {
      source  = "rancher/rancher2"
      version = "4.1.0"
    }
    kubectl = {
      source  = "gavinbunney/kubectl"
      version = "~>1.14"
    }
  }
}
