terraform {
  required_version = ">=1.6.1"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~>5.34"
    }
  }

  backend "s3" {
    bucket         = "folio-terraform"
    region         = "us-east-1"
    key            = "ecr-pullthrough-cache/terraform.tfstate"
    dynamodb_table = "folio-terraform-lock"
  }
}
