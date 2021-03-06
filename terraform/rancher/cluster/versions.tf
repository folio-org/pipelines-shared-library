terraform {
  required_version = ">= 0.15"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "4.16.0"
    }
    rancher2 = {
      source  = "rancher/rancher2"
      version = "1.24.0"
    }
    kubectl = {
      source  = "gavinbunney/kubectl"
      version = "1.14.0"
    }
    helm = {
      source  = "hashicorp/helm"
      version = "2.5.1"
    }
    http = {
      source  = "hashicorp/http"
      version = "2.2.0"
    }
#TODO: temporary workaround for GitHub issue https://github.com/terraform-aws-modules/terraform-aws-eks/issues/2173
    tls = {
      version = "<4.0.0"
    }
  }
}
