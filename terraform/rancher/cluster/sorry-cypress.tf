#Creating a sorry-cypress project in Rancher.
resource "rancher2_project" "sorry-cypress" {
  depends_on                = [module.eks_cluster.eks_managed_node_groups]
  count                     = var.deploy_sorry_cypress ? 1 : 0
  provider                  = rancher2
  name                      = "sorry-cypress"
  cluster_id                = rancher2_cluster_sync.this[0].cluster_id
  enable_project_monitoring = false
  container_resource_limit {
    limits_memory   = "512Mi"
    requests_memory = "256Mi"
  }
}

# Create a new rancher2 Namespace assigned to cluster project
resource "rancher2_namespace" "sorry-cypress" {
  depends_on  = [module.eks_cluster.eks_managed_node_groups]
  count       = var.deploy_sorry_cypress ? 1 : 0
  name        = "sorry-cypress"
  project_id  = rancher2_project.sorry-cypress[0].id
  description = "Project sorry-cypress namespace"
  container_resource_limit {
    limits_memory   = "512Mi"
    requests_memory = "256Mi"
  }
}

resource "rancher2_app_v2" "sorry-cypress" {
  depends_on    = [module.eks_cluster.eks_managed_node_groups]
  count         = var.deploy_sorry_cypress ? 1 : 0
  cluster_id    = rancher2_cluster_sync.this[0].cluster_id
  namespace     = rancher2_namespace.sorry-cypress[0].name
  name          = "sorry-cypress"
  repo_name     = rancher2_catalog_v2.sorry-cypress[0].name
  chart_name    = "sorry-cypress"
  chart_version = "1.9.0"
  values        = <<-EOT
    api:
      replicas: 2
      enabled: true
      resources:
        limits:
          cpu: 200m
          memory: 256Mi
        requests:
          cpu: 100m
          memory: 128Mi
      service:
        port: 4000
        type: NodePort
      ingress:
        ingressClassName: ""
        annotations:
          kubernetes.io/ingress.class: alb
          alb.ingress.kubernetes.io/scheme: internet-facing
          alb.ingress.kubernetes.io/group.name: folio-testing
          alb.ingress.kubernetes.io/listen-ports: '[{"HTTPS":443}]'
          alb.ingress.kubernetes.io/success-codes: 200-399
          alb.ingress.kubernetes.io/healthcheck-path: /
          alb.ingress.kubernetes.io/load-balancer-attributes: idle_timeout.timeout_seconds=4000
        hosts:
          - host: "${module.eks_cluster.cluster_name}-sc-api.${var.root_domain}"
            path: /
    dashboard:
      replicas: 2
      enabled: true
      resources:
        limits:
          cpu: 200m
          memory: 256Mi
        requests:
          cpu: 100m
          memory: 128Mi
      environmentVariables:
        graphQlSchemaUrl: "https://${module.eks_cluster.cluster_name}-sc-api.${var.root_domain}"
      service:
        port: 8080
        type: NodePort
      ingress:
        enabled: true
        ingressClassName: ""
        annotations:
          kubernetes.io/ingress.class: alb
          alb.ingress.kubernetes.io/scheme: internet-facing
          alb.ingress.kubernetes.io/group.name: folio-testing
          alb.ingress.kubernetes.io/listen-ports: '[{"HTTPS":443}]'
          alb.ingress.kubernetes.io/success-codes: 200-399
          alb.ingress.kubernetes.io/healthcheck-path: /
          alb.ingress.kubernetes.io/load-balancer-attributes: idle_timeout.timeout_seconds=4000
        hosts:
          - host: "${module.eks_cluster.cluster_name}-sc-dashboard.${var.root_domain}"
            path: /
    director:
      serviceAccount:
        name: sc-service-account
        create: true
        annotations: {}
      replicas: 2
      resources:
        limits:
          cpu: 200m
          memory: 256Mi
        requests:
          cpu: 100m
          memory: 128Mi
      environmentVariables:
        dashboardUrl: "${module.eks_cluster.cluster_name}-sc-dashboard.${var.root_domain}"
        executionDriver: "../execution/mongo/driver"
        screenshotsDriver: "../screenshots/s3.driver"
        allowedKeys: "secretCypressKey"
      service:
        port: 1234
        type: NodePort
      ingress:
        enabled: true
        ingressClassName: ""
        annotations:
          kubernetes.io/ingress.class: alb
          alb.ingress.kubernetes.io/scheme: internet-facing
          alb.ingress.kubernetes.io/group.name: folio-testing
          alb.ingress.kubernetes.io/listen-ports: '[{"HTTPS":443}]'
          alb.ingress.kubernetes.io/success-codes: 200-399
          alb.ingress.kubernetes.io/healthcheck-path: /
          alb.ingress.kubernetes.io/load-balancer-attributes: idle_timeout.timeout_seconds=4000
        hosts:
          - host: "${module.eks_cluster.cluster_name}-sc-director.${var.root_domain}"
            path: /
    mongodb:
      internal_db:
        enabled: true
      mongoDatabase: "sorry-cypress"
      architecture: standalone
      auth:
        enabled: false
      resources:
        requests:
          cpu: 512m
          memory: 1024Mi
        limits:
          cpu: 2048m
          memory: 4096Mi
      persistence:
        enabled: true
        size: 50Gi
      externalAccess:
        enabled: true
        service:
          type: ClusterIP
    s3:
      bucketName: folio-sorry-cypress
      region: us-west-2
      accessKeyId: "${var.aws_access_key_id}"
      secretAccessKey: "${var.aws_secret_access_key}"
  EOT
}


