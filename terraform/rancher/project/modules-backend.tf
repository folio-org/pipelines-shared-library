# Create rancher2 backend apps in Project namespace
resource "rancher2_app" "backend" {
  depends_on       = [rancher2_app.okapi]
  for_each         = local.backend_map
  project_id       = rancher2_project.this.id
  target_namespace = rancher2_namespace.this.name
  catalog_name     = local.folio_helm_catalog_name
  name             = each.key
  description      = join(" ", ["Folio app", each.key])
  template_name    = each.key
  force_upgrade    = "true"
  answers = {
    "image.repository"          = join("/", [length(regexall(".*SNAPSHOT.*", each.value)) > 0 ? "folioci" : "folioorg", each.key])
    "image.tag"                 = each.value
    "postJob.enabled"           = "false"
    "javaOptions"               = try(local.helm_configs[each.key]["javaOptions"])
    "replicaCount"              = try(local.helm_configs[each.key]["replicaCount"])
    "resources.requests.memory" = try(local.helm_configs[each.key]["resources"]["requests"]["memory"])
    "resources.limits.memory"   = try(local.helm_configs[each.key]["resources"]["limits"]["memory"])
  }
}
