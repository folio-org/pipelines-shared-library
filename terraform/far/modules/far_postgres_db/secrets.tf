resource "random_password" "postgres" {
  count   = var.existing_secret_name == null ? 1 : 0
  length           = var.password_length
  special          = true
  override_special = var.password_override_special

  keepers = {
    namespace = var.namespace_name
  }
}

resource "aws_secretsmanager_secret" "postgres_credentials" {
  count       = var.existing_secret_name == null ? 1 : 0
  name        = "/${var.cluster_name}/${var.namespace_name}/postgres-credentials"
  description = "PostgreSQL credentials for ${var.namespace_name} in ${var.cluster_name} cluster"

  tags = merge(
    var.tags,
    {
      Name        = "${var.cluster_name}-${var.namespace_name}-postgres-credentials"
      Application = "MGR-FAR"
      Terraform   = "true"
    }
  )

  lifecycle {
    prevent_destroy = true
  }
}

resource "aws_secretsmanager_secret_version" "postgres_credentials" {
  count     = var.existing_secret_name == null ? 1 : 0
  secret_id = aws_secretsmanager_secret.postgres_credentials[0].id
  secret_string = jsonencode({
    DB_HOST         = local.postgresql_service_name
    DB_PORT         = tostring(var.db_port)
    DB_DATABASE     = var.db_name
    DB_USERNAME     = var.db_username
    DB_PASSWORD     = random_password.postgres[0].result
    DB_MAXPOOLSIZE  = var.db_max_connections
    DB_CHARSET      = var.db_charset
    DB_QUERYTIMEOUT = var.db_query_timeout
  })

  lifecycle {
    prevent_destroy = true
  }
}
