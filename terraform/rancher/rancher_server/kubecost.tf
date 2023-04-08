data "aws_caller_identity" "current" {}

data "aws_cognito_user_pools" "pool" {
  name = "Kubecost"
}

resource "aws_cognito_user_pool_client" "userpool_client" {
  depends_on                           = [data.aws_cognito_user_pools.pool]
  name                                 = "folio-kubecost"
  user_pool_id                         = tolist(data.aws_cognito_user_pools.pool.ids)[0]
  generate_secret                      = true
  callback_urls                        = ["https://folio-kubecost.${var.root_domain}/oauth2/idpresponse"]
  allowed_oauth_flows_user_pool_client = true
  allowed_oauth_flows                  = ["code"]
  allowed_oauth_scopes                 = ["openid"]
  supported_identity_providers         = ["COGNITO"]
}

# install Kubecost
resource "helm_release" "kubecost" {
  name             = "kubecost"
  repository       = "https://kubecost.github.io/cost-analyzer"
  chart            = "cost-analyzer"
  version          = "1.101.3"
  namespace        = "kubecost"
  create_namespace = true
  values = [<<EOF
    kubecostModel:
      resources:
        limits:
          memory: "4096Mi"
    ingress:
      enabled: true
      hosts:
          - "folio-kubecost.${var.root_domain}"
      paths: ["/*"]
      annotations:
        kubernetes.io/ingress.class: alb
        alb.ingress.kubernetes.io/scheme: internet-facing
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
      clusters:
        - name: folio-dev
          address: "https://folio-dev-kubecost.ci.folio.org"
        - name: folio-testing
          address: "https://folio-testing-kubecost.ci.folio.org"
        - name: folio-perf
          address: "https://folio-perf-kubecost.ci.folio.org"
      currencyCode: "USD"
      clusterName: "kubecost-main"
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
      athenaProjectID: "${data.aws_caller_identity.current.account_id}"
      athenaBucketName: "s3://aws-athena-query-results-kubecost-folio/reports"
      athenaRegion: "${var.aws_region}"
      athenaDatabase: "athenacurcfn_aws_kubecost"
      athenaTable: "aws_kubecost"
      athenaWorkgroup: "primary"
      awsServiceKeyName: "${var.aws_kubecost_access_key_id}"
      awsServiceKeyPassword: "${var.aws_kubecost_secret_access_key}"
      createServiceKeySecret: true
  EOF
  ]
}
