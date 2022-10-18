data "aws_cognito_user_pools" "pool" {
  name = "Kubecost"
}

resource "aws_cognito_user_pool_client" "userpool_client" {
  depends_on                           = [data.aws_cognito_user_pools.pool]
  name                                 = "${module.eks_cluster.cluster_id}"
  user_pool_id                         = "${tolist(data.aws_cognito_user_pools.pool.ids)[0]}"
  generate_secret                      = true
  callback_urls                        = ["https://${module.eks_cluster.cluster_id}-kubecost.${var.root_domain}/oauth2/idpresponse"]
  allowed_oauth_flows_user_pool_client = true
  allowed_oauth_flows                  = ["code"]
  allowed_oauth_scopes                 = ["openid"]
  supported_identity_providers         = ["COGNITO"]
}

#Creating a new project in Rancher.
resource "rancher2_project" "kubecost" {
  depends_on                = [rancher2_cluster_sync.this]
  count                     = var.register_in_rancher ? 1 : 0
  provider                  = rancher2
  name                      = "Kubecost"
  cluster_id                = rancher2_cluster_sync.this[0].cluster_id
  enable_project_monitoring = false
  container_resource_limit {
    limits_memory   = "512Mi"
    requests_memory = "400Mi"
  }
}

# Create a new rancher2 Namespace assigned to cluster project
resource "rancher2_namespace" "kubecost" {
  depends_on  = [rancher2_cluster_sync.this]
  count       = var.register_in_rancher ? 1 : 0
  name        = "kubecost"
  project_id  = rancher2_project.kubecost[0].id
  description = "Project kubecost namespace"
  container_resource_limit {
    limits_memory   = "512Mi"
    requests_memory = "400Mi"
  }
}

# Create rancher2 Kubecost app in kubecost namespace
resource "rancher2_app_v2" "kubecost" {
  depends_on    = [rancher2_catalog_v2.kubecost]
  count         = var.register_in_rancher ? 1 : 0
  cluster_id    = rancher2_cluster_sync.this[0].cluster_id
  namespace     = rancher2_namespace.kubecost[0].name
  name          = "kubecost"
  repo_name     = "cost-analyzer"
  chart_name    = "cost-analyzer"
  chart_version = "1.89.1"
  force_upgrade = "true"
  values        = <<-EOT
    ingress:
      enabled: true
      hosts: 
        - "${module.eks_cluster.cluster_id}-kubecost.${var.root_domain}"
      paths: ["/*"]
      annotations:
        kubernetes.io/ingress.class: alb
        alb.ingress.kubernetes.io/scheme: internet-facing
        alb.ingress.kubernetes.io/group.name: ${module.eks_cluster.cluster_id}
        alb.ingress.kubernetes.io/listen-ports: '[{"HTTPS":443}]'
        alb.ingress.kubernetes.io/success-codes: 200-399
        alb.ingress.kubernetes.io/healthcheck-path: /
        alb.ingress.kubernetes.io/load-balancer-attributes: idle_timeout.timeout_seconds=4000
        alb.ingress.kubernetes.io/auth-idp-cognito: '{"UserPoolArn":"${tolist(data.aws_cognito_user_pools.pool.arns)[0]}","UserPoolClientId":"${aws_cognito_user_pool_client.userpool_client.id}", "UserPoolDomain":"folio-kubecost"}'
        alb.ingress.kubernetes.io/auth-on-unauthenticated-request: authenticate
        alb.ingress.kubernetes.io/auth-scope: openid
        alb.ingress.kubernetes.io/auth-session-cookie: AWSELBAuthSessionCookie
        alb.ingress.kubernetes.io/auth-session-timeout: "3600"
        alb.ingress.kubernetes.io/auth-type: cognito
    service:
      type: NodePort
    kubecostProductConfigs:
      currencyCode: "USD"
  EOT
}
