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
      # Provider major version mirrors Rancher server minor version (from v13 onwards).
      # v4.1.0 targets Rancher 2.8.x — it fails on 2.13 with:
      #   "Unknown schema type [rkeK8sSystemImage]"
      # because the v4 provider probes RKE1 schemas at init time, which Rancher 2.13
      # no longer exposes (RKE1 is deprecated/removed).
      # v13.x is the correct provider for Rancher 2.13.x.
      version = "~>13.0"
    }
    kubectl = {
      source  = "gavinbunney/kubectl"
      version = "~>1.14"
    }
  }
}
