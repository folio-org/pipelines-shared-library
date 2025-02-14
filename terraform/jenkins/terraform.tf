terraform {
  backend "s3" {
    bucket         = "folio-terraform"
    region         = "us-east-1"
    key            = "jenkins/terraform.tfstate"
    dynamodb_table = "folio-terraform-lock"
  }
}
