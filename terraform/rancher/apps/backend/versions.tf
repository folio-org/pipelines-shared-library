terraform {
  required_version = ">= 0.15"
  required_providers {
    rancher2 = {
      source  = "rancher/rancher2"
      version = "1.24.0"
    }
    http = {
      source  = "hashicorp/http"
      version = "2.2.0"
    }
  }
}
