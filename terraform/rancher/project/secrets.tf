resource "rancher2_secret" "okapi-credentials" {
  name         = "okapi-credentials"
  project_id   = rancher2_project.this.id
  namespace_id = rancher2_namespace.this.id
  data = {
    OKAPI_URL          = var.eureka ? base64encode("http://kong-admin-api-${rancher2_namespace.this.id}") : base64encode("http://okapi:9130")
    OKAPI_HOST         = var.eureka ? base64encode("kong-admin-api-${rancher2_namespace.this.id}") : base64encode("okapi")
    OKAPI_PORT         = var.eureka ? base64encode("80") : base64encode("9130")
    OKAPI_SERVICE_URL  = var.eureka ? base64encode("http://kong-admin-api-${rancher2_namespace.this.id}") : base64encode("http://okapi:9130")
    OKAPI_SERVICE_HOST = var.eureka ? base64encode("kong-admin-api-${rancher2_namespace.this.id}") : base64encode("okapi")
    OKAPI_SERVICE_PORT = var.eureka ? base64encode("80") : base64encode("9130")
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

resource "rancher2_secret" "eureka_common" {
  count        = var.eureka ? 1 : 0
  name         = "eureka-common"
  project_id   = rancher2_project.this.id
  namespace_id = rancher2_namespace.this.name
  data = {
    KC_ADMIN_CLIENT_ID          = base64encode("folio-backend-admin-client")
    KC_ADMIN_CLIENT_SECRET      = base64encode("folio-backend-admin-client")
    KC_LOGIN_CLIENT_SUFFIX      = base64encode("-application")
    KC_IMPORT_ENABLED           = base64encode("true")
    KC_URL                      = base64encode("https://${local.keycloak_url}")
    KC_INTEGRATION_ENABLED      = base64encode("true")
    KONG_ADMIN_URL              = base64encode("http://kong-admin-api-${rancher2_namespace.this.id}")
    KONG_INTEGRATION_ENABLED    = base64encode("true")
    OKAPI_INTEGRATION_ENABLED   = base64encode(var.okapi_integration_enabled)
    SECRET_STORE_AWS_SSM_REGION = base64encode(var.aws_region)
    SECRET_STORE_TYPE           = base64encode(var.secure_store_type)
    SECURITY_ENABLED            = base64encode("true")
    SYSTEM_USER_CREATE          = base64encode("false")
    "tenant.url"                = base64encode("http://mgr-tenants")
    "am.url"                    = base64encode("http://mgr-applications")
    TE_URL                      = base64encode("http://mgr-tenant-entitlements")
    MOD_USERS_BL                = base64encode("http://mod-users-bl")
    MOD_USERS_KEYCLOAK_URL      = base64encode("http://mod-users-keycloak")
    #    SECRET_STORE_AWS_SSM_ACCESS_KEY = base64encode(var.s3_postgres_backups_access_key)
    #    SECRET_STORE_AWS_SSM_SECRET_KEY = base64encode(var.s3_postgres_backups_secret_key)
    #    SECRET_STORE_AWS_SSM_USE_IAM    = base64encode("false") //TODO could be switched on upon EUREKA-210 completion
  }
}
#Must have SSM Eureka parameters
resource "aws_ssm_parameter" "ssm_param" {
  for_each = var.eureka ? toset(local.ssm_params) : []
  name     = join("_", [join("-", [var.rancher_cluster_name, rancher2_namespace.this.name]), each.value])
  type     = "SecureString"
  value    = "SecretPassword"
}
