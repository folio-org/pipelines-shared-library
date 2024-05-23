resource "postgresql_role" "keycloak" {
  count = var.eureka ? 1 : 0
  name     = "keycloak_admin"
  login    = true
  password = var.pg_password
}

resource "postgresql_role" "kong" {
  count = var.eureka ? 1 : 0
  name     = "kong_admin"
  login    = true
  password = var.pg_password
}

resource "postgresql_database" "keycloak" {
  count = var.eureka ? 1 : 0
  depends_on = [postgresql_role.keycloak]
  name              = "keycloak"
  owner             = postgresql_role.keycloak[0].id
  connection_limit  = -1
  allow_connections = true
}

resource "postgresql_database" "kong" {
  count = var.eureka ? 1 : 0
  depends_on = [postgresql_role.kong]
  name              = "kong"
  owner             = postgresql_role.kong[0].id
  connection_limit  = -1
  allow_connections = true
}
