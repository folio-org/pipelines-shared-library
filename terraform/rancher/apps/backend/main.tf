data "rancher2_cluster" "this" {
  name = var.cluster_name
}

data "rancher2_project" "this" {
  name       = var.project_name
  cluster_id = data.rancher2_cluster.this.id
}

resource "rancher2_app" "folio_backend" {
  for_each         = local.backend_map
  project_id       = data.rancher2_project.this.id
  target_namespace = data.rancher2_project.this.name
  catalog_name     = join(":", [element(split(":", data.rancher2_project.this.id), 1), join("-", [data.rancher2_project.this.name, "helmcharts"])])
  name             = each.key
  description      = join(" ", ["Folio app", each.key])
  force_upgrade    = "true"
  template_name    = each.key
  answers = {
    "postJob.enabled"  = "false"
    "image.repository" = join("/", [length(regexall(".*SNAPSHOT.*", each.value)) > 0 ? "folioci" : "folioorg", each.key])
    "image.tag"        = each.value
    "javaOptions"               = try(local.modules_config[(each.key)]["javaOptions"])
    "replicaCount"              = local.modules_config[(each.key)]["replicaCount"]
    "resources.requests.memory" = local.modules_config[(each.key)]["resources"]["requests"]["memory"]
    "resources.limits.memory"   = local.modules_config[(each.key)]["resources"]["limits"]["memory"]
  }
}
