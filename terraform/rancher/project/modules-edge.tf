# Create rancher2 Edge apps in Project namespace
resource "rancher2_app_v2" "edge" {
  depends_on    = [rancher2_app_v2.okapi]
  for_each      = local.edge-map
  cluster_id    = data.rancher2_cluster.this.id
  namespace     = rancher2_namespace.this.name
  name          = each.key
  repo_name     = local.folio_helm_repo_name
  chart_name    = each.key
  wait          = false
  force_upgrade = true
  values        = <<-EOT
    image:
      repository: ${join("/", [length(regexall(".*SNAPSHOT.*", each.value)) > 0 ? "folioci" : "folioorg", each.key])}
      tag: ${each.value}
    resources:
      requests:
        memory: ${try(local.helm_configs[each.key]["resources"]["requests"]["memory"])}
      limits:
        memory: ${try(local.helm_configs[each.key]["resources"]["limits"]["memory"])}
    service:
      type: NodePort
    ingress:
      enabled: true
      hosts:
        - host: ${join(".", [join("-", [data.rancher2_cluster.this.name, var.rancher_project_name, "okapi"]), var.root_domain])}
          paths:
            - /${each.key}/*
      annotations:
        kubernetes.io/ingress.class: alb
        alb.ingress.kubernetes.io/scheme: internet-facing
        alb.ingress.kubernetes.io/group.name: ${local.group_name}
        alb.ingress.kubernetes.io/listen-ports: '[{"HTTPS":443}]'
        alb.ingress.kubernetes.io/success-codes: 200-399
        alb.ingress.kubernetes.io/healthcheck-path: /_/version
    postJob:
      enabled: false
    javaOptions: ${try(local.helm_configs[each.key]["javaOptions"])}
    replicaCount: ${try(local.helm_configs[each.key]["replicaCount"])}
  EOT
}

# Create rancher2 Edge-sip2 app in a default Project namespace
resource "rancher2_app_v2" "edge-sip2" {
  depends_on    = [rancher2_app_v2.okapi]
  for_each      = local.edge-sip2-map
  cluster_id    = data.rancher2_cluster.this.id
  namespace     = rancher2_namespace.this.name
  name          = each.key
  repo_name     = local.folio_helm_repo_name
  chart_name    = each.key
  wait          = false
  force_upgrade = true
  values        = <<-EOT
    image:
      repository: ${join("/", [length(regexall(".*SNAPSHOT.*", each.value)) > 0 ? "folioci" : "folioorg", each.key])}
      tag: ${each.value}
    resources:
      requests:
        memory: ${try(local.helm_configs[each.key]["resources"]["requests"]["memory"])}
      limits:
        memory: ${try(local.helm_configs[each.key]["resources"]["limits"]["memory"])}
    service:
      type: LoadBalancer

      annotations:
        service.beta.kubernetes.io/aws-load-balancer-type: nlb
        external-dns.alpha.kubernetes.io/hostname: ${join(".", [join("-", [data.rancher2_cluster.this.name, var.rancher_project_name, "sip2"]), var.root_domain])}
    javaOptions: ${try(local.helm_configs[each.key]["javaOptions"])}
    replicaCount: ${try(local.helm_configs[each.key]["replicaCount"])}
  EOT
}
