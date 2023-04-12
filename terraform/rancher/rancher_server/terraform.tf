terraform {
  backend "s3" {
    bucket         = "folio-terraform"
    region         = "us-east-1"
    key            = "folio-rancher/terraform.tfstate"
    dynamodb_table = "folio-terraform-lock"
  }
}
