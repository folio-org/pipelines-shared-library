data "http" "install" {
  url = local.install_json_url
  request_headers = {
    Accept = "application/json"
  }
}

locals {
  github_url       = "https://raw.githubusercontent.com/folio-org"
  install_json_url = join("/", [local.github_url, var.repository, var.branch, "install.json"])
  modules_config   = jsondecode(file("${path.module}/resources/${var.env_config}.json"))
  modules_list     = var.modules_json != "" ? jsondecode(var.modules_json)[*]["id"] : jsondecode(data.http.install.body)[*]["id"]
  modules_map = {
    for id in local.modules_list : regex("^(.*)-(\\d*\\.\\d*\\.\\d*.*)$", id)[0] => regex("^(.*)-(\\d*\\.\\d*\\.\\d*.*)$", id)[1]
  }
  backend_map = {
    for k, v in local.modules_map : k => v if substr(k, 0, length("mod-")) == "mod-"
  }
}
