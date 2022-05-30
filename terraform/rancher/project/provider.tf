provider "rancher2" {
  api_url   = var.rancher_server_url
  token_key = var.rancher_token_key
}

provider "aws" {
  region = var.aws_region
}

hashicorp_tfvars = {
  source  = "hashicorp/tfvars"
  version = "~> 1.0"
}
