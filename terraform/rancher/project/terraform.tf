terraform {
  backend "s3" {
    bucket               = "folio-terraform"
    region               = "us-east-1"
    workspace_key_prefix = "rancher-project"
    key                  = "terraform.tfstate"
    dynamodb_table       = "folio-terraform-lock"
  }
}
