terraform {
  required_version = ">=1.6.1"
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
    helm = {
      source  = "hashicorp/helm"
      version = "2.12.1"
    }
    time = {
      source  = "hashicorp/time"
      version = "0.11.2"
    }

    postgresql = {
      source  = "cyrilgdn/postgresql"
      version = "1.22.0"
    }

    kubernetes = {
      source  = "hashicorp/kubernetes"
      version = "2.30.0"
    }
  }
}
