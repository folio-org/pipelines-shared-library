data "aws_secretsmanager_secret" "existing_postgres_credentials" {
  count = var.existing_secret_name != null ? 1 : 0
  name  = var.existing_secret_name
}

data "aws_secretsmanager_secret_version" "existing_postgres_credentials" {
  count     = var.existing_secret_name != null ? 1 : 0
  secret_id = data.aws_secretsmanager_secret.existing_postgres_credentials[0].id
}

locals {
  secret_data = var.existing_secret_name != null ? jsondecode(data.aws_secretsmanager_secret_version.existing_postgres_credentials[0].secret_string) : jsondecode(aws_secretsmanager_secret_version.postgres_credentials[0].secret_string)
}
