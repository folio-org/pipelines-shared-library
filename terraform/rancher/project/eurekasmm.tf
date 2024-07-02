resource "aws_ssm_parameter" "ssm_param" {
  for_each = var.eureka ? toset(local.ssm_params) : []
  name     = join("_", [join("-", [var.rancher_cluster_name, rancher2_namespace.this.name]), each.value])
  type     = "SecureString"
  value    = "SecretPassword"
}
