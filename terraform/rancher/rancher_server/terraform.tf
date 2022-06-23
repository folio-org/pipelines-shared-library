terraform {
  backend "s3" {
    bucket         = "folio-terraform"
    region         = "us-east-1"
    key            = "rancher-server/terraform.tfstate"
    dynamodb_table = "folio-terraform-lock"
  }
}
