# Create rancher2 OKAPI app in Project namespace
resource "rancher2_app_v2" "backend" {
  depends_on    = [rancher2_app_v2.okapi]
  for_each      = local.backend_map
  cluster_id    = data.rancher2_cluster.this.id
  namespace     = rancher2_namespace.this.name
  name          = each.key
  repo_name     = local.folio_helm_repo_name
  chart_name    = each.key
  force_upgrade = true
  wait          = false
  values        = <<-EOT
    image:
      repository: ${join("/", [length(regexall(".*SNAPSHOT.*", each.value)) > 0 ? "folioci" : "folioorg", each.key])}
      tag: ${each.value}
    postJob:
      enabled: false
    javaOptions: ${try(local.helm_configs[each.key]["javaOptions"])}
    replicaCount: ${try(local.helm_configs[each.key]["replicaCount"])}
    resources:
      requests:
        memory: ${try(local.helm_configs[each.key]["resources"]["requests"]["memory"])}
      limits:
        memory: ${try(local.helm_configs[each.key]["resources"]["limits"]["memory"])}
  EOT
}
