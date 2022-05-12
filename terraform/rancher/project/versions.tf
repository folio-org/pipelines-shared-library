terraform {
  required_version = ">= 0.15"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "4.12.1"
    }
    rancher2 = {
      source  = "rancher/rancher2"
      version = "1.23.0"
    }
    random = {
      source  = "hashicorp/random"
      version = "3.1.3"
    }
  }
}
