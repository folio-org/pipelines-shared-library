resource "rancher2_secret" "okapi-credentials" {
  name         = "okapi-credentials"
  project_id   = rancher2_project.this.id
  namespace_id = rancher2_namespace.this.id
  data = {
    OKAPI_URL          = base64encode("http://okapi:9130")
    OKAPI_HOST         = base64encode("okapi")
    OKAPI_PORT         = base64encode("9130")
    OKAPI_SERVICE_URL  = base64encode("http://okapi:9130")
    OKAPI_SERVICE_HOST = base64encode("okapi")
    OKAPI_SERVICE_PORT = base64encode("9130")
  }
}

resource "rancher2_secret" "system_user" {
  for_each = toset(local.system_user_modules)

  name         = "${each.value}-systemuser"
  project_id   = rancher2_project.this.id
  namespace_id = rancher2_namespace.this.name
  data = {
    SYSTEM_USER_NAME     = base64encode("${each.value}-system")
    SYSTEM_USER_PASSWORD = base64encode(random_password.system_user_password[each.value].result)
  }
}

resource "rancher2_secret" "s3-postgres-backups-credentials" {
  name         = "s3-postgres-backups-credentials"
  project_id   = rancher2_project.this.id
  namespace_id = rancher2_namespace.this.name
  data = {
    AWS_ACCESS_KEY_ID     = base64encode(var.s3_postgres_backups_access_key)
    AWS_SECRET_ACCESS_KEY = base64encode(var.s3_postgres_backups_secret_key)
  }
}

resource "random_password" "system_user_password" {
  for_each = toset(local.system_user_modules)

  length      = 16
  special     = false
  numeric     = true
  upper       = true
  lower       = true
  min_lower   = 1
  min_numeric = 1
  min_upper   = 1
}
