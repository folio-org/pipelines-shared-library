terraform {
  backend "s3" {
    bucket               = "folio-terraform"
    region               = "us-east-1"
    key                  = "far/terraform.tfstate"
    workspace_key_prefix = "far/workspaces"
    use_lockfile         = true
  }
}
