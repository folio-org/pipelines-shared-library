provider "rancher2" {
  api_url   = var.rancher_server_url
  token_key = var.rancher_token_key
}

provider "aws" {
  region = var.aws_region
}

provider "hashicorp_tfvars" {
  filename = env_type.tfvars
}
