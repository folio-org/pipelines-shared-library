locals {
  repository_url = join("", [
    "https://raw.githubusercontent.com/folio-org/platform-", var.folio_repository, "/", var.folio_release
  ])
  installUrl        = join("/", [local.repository_url, "install.json"])
  full-modules-list = jsondecode(data.http.install.body)[*]["id"]
  full-modules-map = {
    for name in local.full-modules-list : regex("^(.*)-((0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*).*)$", name)[0] => regex("^(.*)-((0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*).*)$", name)[1]
  }
  backend-map = {
    for k, v in local.full-modules-map : k => v if substr(k, 0, length("mod-")) == "mod-"
  }
  edge-map = {
    for k, v in local.full-modules-map : k => v if substr(k, 0, length("edge-")) == "edge-" && !contains([k], "edge-sip2")
  }
  edge-sip2-map = {
    for k, v in local.full-modules-map : k => v if contains([k], "edge-sip2")
  }
}

data "http" "install" {
  url = local.installUrl
  request_headers = {
    Accept = "application/json"
  }
}
