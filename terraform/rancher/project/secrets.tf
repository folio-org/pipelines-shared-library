resource "rancher2_secret" "okapi-credentials" {
  name         = "okapi-credentials"
  project_id   = rancher2_project.this.id
  namespace_id = rancher2_namespace.this.id
  data = {
    OKAPI_URL          = var.eureka ? base64encode("http://localhost:8082") : base64encode("http://okapi:9130")
    OKAPI_HOST         = var.eureka ? base64encode("localhost") : base64encode("okapi")
    OKAPI_PORT         = var.eureka ? base64encode("8082") : base64encode("9130")
    OKAPI_SERVICE_URL  = var.eureka ? base64encode("http://localhost:8082") : base64encode("http://okapi:9130")
    OKAPI_SERVICE_HOST = var.eureka ? base64encode("localhost") : base64encode("okapi")
    OKAPI_SERVICE_PORT = var.eureka ? base64encode("8082") : base64encode("9130")
  }
}

resource "rancher2_secret" "eureka-edge" {
  name         = "eureka-edge"
  count        = var.eureka ? 1 : 0
  project_id   = rancher2_project.this.id
  namespace_id = rancher2_namespace.this.id
  data = {
    OKAPI_HOST = base64encode("kong-${rancher2_namespace.this.id}")
    OKAPI_PORT = base64encode("8000")
  }
}

resource "rancher2_secret" "system_user" {
  for_each = toset(local.system_user_modules)

  name         = "${each.value}-systemuser"
  project_id   = rancher2_project.this.id
  namespace_id = rancher2_namespace.this.name
  data = merge(
    {
      SYSTEM_USER_NAME     = base64encode("${each.value == "mod-consortia-keycloak" ? "consortia-system-user" : each.value}${var.eureka ? "" : "-system"}")
      SYSTEM_USER_USERNAME = base64encode("${each.value == "mod-consortia-keycloak" ? "consortia-system-user" : each.value}${var.eureka ? "" : "-system"}")
    },
    var.eureka ? {
      SYSTEM_USER_CREATE        = base64encode("false")
      SYSTEM_USER_ENABLED       = base64encode("false")
      FOLIO_SYSTEM_USER_ENABLED = base64encode("false")
      } : {
      SYSTEM_USER_PASSWORD = base64encode(each.value == "mod-consortia-keycloak" ? "mod-consortia-keycloak" : random_password.system_user_password[each.value].result)
    }
  )
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
    KC_ADMIN_CLIENT_ID     = base64encode("folio-backend-admin-client")
    KC_ADMIN_CLIENT_SECRET = base64encode("folio-backend-admin-client")
    KC_LOGIN_CLIENT_SUFFIX = base64encode("-application")
    KC_ADMIN_PASSWORD      = base64encode("SecretPassword")
    KC_CONFIG_TTL          = base64encode("3600s")
    KC_SERVICE_CLIENT_ID   = base64encode("sidecar-module-access-client")
    KC_IMPORT_ENABLED      = base64encode("true")
    #KC_URL                                        = base64encode("https://${local.keycloak_url}")
    KC_URL                                       = base64encode("http://keycloak-${rancher2_namespace.this.id}-headless.${rancher2_namespace.this.id}.svc.cluster.local:8080")
    KC_INTEGRATION_ENABLED                       = base64encode("true")
    keycloak_url      = join(".", [join("-", [data.rancher2_cluster.this.name, var.rancher_project_name, "keycloak"]), var.root_domain])
    keycloak_url      = join(".", [join("-", [data.rancher2_cluster.this.name, var.rancher_project_name, "keycloak"]), var.root_domain])
    KC_IDENTITY_PROVIDER_BASE_URL                = base64encode("http://${local.keycloak_url}")
    KONG_ADMIN_URL                               = base64encode("http://kong-admin-api-${rancher2_namespace.this.id}")
    KONG_INTEGRATION_ENABLED                     = base64encode("true")
    OKAPI_INTEGRATION_ENABLED                    = base64encode(var.okapi_integration_enabled)
    SECRET_STORE_AWS_SSM_REGION                  = base64encode(var.aws_region)
    SECRET_STORE_TYPE                            = base64encode(var.secure_store_type)
    SECURITY_ENABLED                             = base64encode("false")
    "tenant.url"                                 = base64encode("http://mgr-tenants")      #DO NOT MODIFY THIS VALUE! If you modify it, align helm charts(templates) too!
    "am.url"                                     = base64encode("http://mgr-applications") #DO NOT MODIFY THIS VALUE! If you modify it, align helm charts(templates) too!
    TE_URL                                       = base64encode("http://mgr-tenant-entitlements")
    MOD_USERS_BL                                 = base64encode("http://mod-users-bl:8082")
    MOD_USERS_KEYCLOAK_URL                       = base64encode("http://mod-users-keycloak:8082")
    SIDECAR_FORWARD_UNKNOWN_REQUESTS_DESTINATION = base64encode("http://kong-${rancher2_namespace.this.id}:8000")
  }
}
#Must have SSM Eureka parameters
resource "aws_ssm_parameter" "ssm_param" {
  for_each = var.eureka ? toset(local.ssm_params) : []
  name     = join("_", [join("-", [var.rancher_cluster_name, rancher2_namespace.this.name]), each.value])
  type     = "SecureString"
  value    = "SecretPassword"
  lifecycle {
    create_before_destroy = true
  }
}
