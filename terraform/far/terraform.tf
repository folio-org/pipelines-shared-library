terraform {
  backend "s3" {
    bucket       = "folio-terraform"
    region       = "us-east-1"
    key          = "far/terraform.tfstate"
    use_lockfile = true
  }
}
