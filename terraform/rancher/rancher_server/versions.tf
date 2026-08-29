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
      # Provider major version mirrors Rancher server minor version (from v13 onwards).
      # v14.x is the correct provider for Rancher 2.14.x; aligned with cluster/ and project/ modules.
      version = "~>14.0"
    }
    helm = {
      source  = "hashicorp/helm"
      version = "~>2.11" # aligned with cluster/ module
    }
  }
}
