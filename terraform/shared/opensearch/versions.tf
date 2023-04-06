terraform {
  required_version = ">= 0.15"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "4.24.0"
    }
    random = {
      source  = "hashicorp/random"
      version = "3.3.2"
    }
  }
}
