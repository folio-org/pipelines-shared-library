terraform {
  required_version = ">=1.6.1"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~>5.34"
    }
    rancher2 = {
      source  = "rancher/rancher2"
      # Provider major version mirrors Rancher server minor version (from v13 onwards).
      # v14.x is the correct provider for Rancher 2.14.x.
      # (v4.1.0 fails on 2.13+ with "Unknown schema type [rkeK8sSystemImage]" because
      # the v4 provider probes RKE1 schemas at init time, which 2.13+ no longer exposes.)
      version = "~>14.0"
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
