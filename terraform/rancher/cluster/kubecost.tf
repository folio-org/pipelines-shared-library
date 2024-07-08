/* Temporary conflicts with Prometheus/Grafana installation. Will be resolved in scope of RANCHER-541 */

/* Disable auth for Kubecist UI. With auth cannot connect context in Main Kubecost */
# data "aws_cognito_user_pools" "pool" {
#   name = "Kubecost"
# }

# resource "aws_cognito_user_pool_client" "userpool_client" {
#   depends_on                           = [data.aws_cognito_user_pools.pool]
#   name                                 = module.eks_cluster.cluster_name
#   user_pool_id                         = tolist(data.aws_cognito_user_pools.pool.ids)[0]
#   generate_secret                      = true
#   callback_urls                        = ["https://${module.eks_cluster.cluster_name}-kubecost.${var.root_domain}/oauth2/idpresponse"]
#   allowed_oauth_flows_user_pool_client = true
#   allowed_oauth_flows                  = ["code"]
#   allowed_oauth_scopes                 = ["openid"]
#   supported_identity_providers         = ["COGNITO"]
# }

#Creating a new project in Rancher.
resource "rancher2_project" "kubecost" {
  count                     = var.deploy_kubecost ? 1 : 0
  provider                  = rancher2
  name                      = "kubecost"
  cluster_id                = rancher2_cluster_sync.this[0].cluster_id
  enable_project_monitoring = false
  container_resource_limit {
    limits_memory   = "512Mi"
    requests_memory = "256Mi"
  }
}

# Create a new rancher2 Namespace assigned to cluster project
resource "rancher2_namespace" "kubecost" {
  count       = var.deploy_kubecost ? 1 : 0
  name        = "kubecost"
  project_id  = rancher2_project.kubecost[0].id
  description = "Project kubecost namespace"
  container_resource_limit {
    limits_memory   = "512Mi"
    requests_memory = "256Mi"
  }
}

# Create rancher2 Kubecost app in kubecost namespace
resource "rancher2_app_v2" "kubecost" {
  count         = var.deploy_kubecost ? 1 : 0
  cluster_id    = rancher2_cluster_sync.this[0].cluster_id
  namespace     = rancher2_namespace.kubecost[0].name
  name          = "kubecost"
  repo_name     = rancher2_catalog_v2.kubecost[0].name
  chart_name    = "cost-analyzer"
  chart_version = "1.101.3"
  values        = <<-EOT
    kubecostModel:
      resources:
        limits:
          memory: "4096Mi"
    ingress:
      enabled: true
      hosts:
        - "${module.eks_cluster.cluster_name}-kubecost.${var.root_domain}"
      paths: ["/*"]
      annotations:
        kubernetes.io/ingress.class: alb
        alb.ingress.kubernetes.io/scheme: internet-facing
        alb.ingress.kubernetes.io/group.name: ${module.eks_cluster.cluster_name}
        alb.ingress.kubernetes.io/listen-ports: '[{"HTTPS":443}]'
        alb.ingress.kubernetes.io/success-codes: 200-399
        alb.ingress.kubernetes.io/healthcheck-path: /
        alb.ingress.kubernetes.io/load-balancer-attributes: idle_timeout.timeout_seconds=4000
    service:
      type: NodePort
    kubecostProductConfigs:
      currencyCode: "USD"
      clusterName: ${module.eks_cluster.cluster_name}
      labelMappingConfigs:
        enabled: true
        owner_label: "owner"
        team_label: "team"
        department_label: "department"
        product_label: "product"
        environment_label: "env"
        namespace_external_label: "kubernetes_namespace"
        cluster_external_label: "kubernetes_cluster"
        controller_external_label: "kubernetes_controller"
        product_external_label: "kubernetes_label_app"
        service_external_label: "kubernetes_service"
        deployment_external_label: "kubernetes_deployment"
        owner_external_label: "kubernetes_label_owner"
        team_external_label: "kubernetes_label_team"
        environment_external_label: "kubernetes_label_env"
        department_external_label: "kubernetes_label_department"
        statefulset_external_label: "kubernetes_statefulset"
        daemonset_external_label: "kubernetes_daemonset"
        pod_external_label: "kubernetes_pod"
      productKey:
        key: "${var.kubecost_licence_key}"
        enabled: true
    global:
      grafana:
        enabled: false
        domainName: ${rancher2_app_v2.prometheus[0].name}-grafana.${rancher2_namespace.monitoring[0].name}.svc
      prometheus:
        enabled: false
        nodeExporter:
          enabled: false
        serviceAccounts:
          nodeExporter:
            create: false
        kubeStateMetrics:
          enabled: false
        kube-state-metrics:
          disabled: true
        fqdn: http://${rancher2_app_v2.prometheus[0].name}-prometheus.${rancher2_namespace.monitoring[0].name}.svc:9090
  EOT
}
