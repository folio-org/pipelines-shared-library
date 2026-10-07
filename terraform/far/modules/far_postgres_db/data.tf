data "aws_secretsmanager_secret" "existing_postgres_credentials" {
  count = var.existing_secret_name != null && var.credentials_store == "secretsmanager" ? 1 : 0
  name  = var.existing_secret_name
}

data "aws_secretsmanager_secret_version" "existing_postgres_credentials" {
  count     = var.existing_secret_name != null && var.credentials_store == "secretsmanager" ? 1 : 0
  secret_id = data.aws_secretsmanager_secret.existing_postgres_credentials[0].id
}

data "aws_ssm_parameter" "existing_postgres_credentials" {
  count           = var.existing_secret_name != null && var.credentials_store == "ssm" ? 1 : 0
  name            = var.existing_secret_name
  with_decryption = true
}

locals {
  secret_data = jsondecode(one(concat(
    data.aws_secretsmanager_secret_version.existing_postgres_credentials[*].secret_string,
    data.aws_ssm_parameter.existing_postgres_credentials[*].value,
    aws_secretsmanager_secret_version.postgres_credentials[*].secret_string,
    aws_ssm_parameter.postgres_credentials[*].value
  )))
}
