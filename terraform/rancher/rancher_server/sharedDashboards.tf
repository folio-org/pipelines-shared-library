data "aws_ssm_parameter" "kafka_parameters" {
  count = var.kafka_shared_name != "" ? 1 : 0
  name  = var.kafka_shared_name
}

data "aws_ssm_parameter" "opensearch_parameters" {
  count = var.opensearch_shared_name != "" ? 1 : 0
  name  = var.opensearch_shared_name
}

locals {
  kafka_parameter      = try(nonsensitive(jsondecode(data.aws_ssm_parameter.kafka_parameters.0.value)), "")
  opensearch_parameter = try(nonsensitive(jsondecode(data.aws_ssm_parameter.opensearch_parameters.0.value)), "")
}

# Only create Kafka UI if kafka_shared_name set
resource "helm_release" "kafka-ui" {
  count            = var.kafka_shared_name != "" ? 1 : 0
  name             = "kafka-ui"
  repository       = "https://provectus.github.io/kafka-ui-charts"
  chart            = "kafka-ui"
  version          = "0.7.2"
  namespace        = "shared-dashboards"
  create_namespace = true
  values = [<<EOF
    service:
      type: NodePort
    ingress:
      host: "folio-kafka-ui.${var.root_domain}"
      path: "/"
      enabled: true
      annotations:
        kubernetes.io/ingress.class: alb
        alb.ingress.kubernetes.io/scheme: internet-facing
        alb.ingress.kubernetes.io/listen-ports: '[{"HTTPS":443}]'
        alb.ingress.kubernetes.io/success-codes: 200-399
        alb.ingress.kubernetes.io/group.name: ${var.rancher_cluster_name}

    yamlApplicationConfig:
      kafka:
        clusters:
          - name: "shared-kafka"
            bootstrapServers: "${local.kafka_parameter["KAFKA_HOST"]}:${local.kafka_parameter["KAFKA_PORT"]}"
      auth:
        type: disabled
      management:
        health:
          ldap:
            enabled: false
  EOF
  ]
}

# Only create Opensearch Dashboard if opensearch_shared_name set
resource "helm_release" "opensearch-dashboards" {
  count            = var.opensearch_shared_name != "" ? 1 : 0
  name             = "opensearch-dashboards"
  repository       = "https://opensearch-project.github.io/helm-charts"
  chart            = "opensearch-dashboards"
  version          = "2.11.1"
  namespace        = "shared-dashboards"
  create_namespace = true
  values = [<<EOF
    image:
      tag: "1.3.2"
    service:
      type: NodePort
    clusterName: "shared-opensearch"
    masterService: "shared-opensearch"
    replicas: 1
    opensearchHosts: "${base64decode(local.opensearch_parameter["ELASTICSEARCH_URL"])}"
    extraEnvs:
      - name: DISABLE_SECURITY_DASHBOARDS_PLUGIN
        value: "true"
      - name: OPENSEARCH_SSL_VERIFICATIONMODE
        value: "full"
      - name: OPENSEARCH_USERNAME
        value: "${base64decode(local.opensearch_parameter["ELASTICSEARCH_USERNAME"])}"
      - name: OPENSEARCH_PASSWORD
        value: "${base64decode(local.opensearch_parameter["ELASTICSEARCH_PASSWORD"])}"
    resources:
      requests:
        memory: 2048Mi
      limits:
        memory: 3072Mi
    ingress:
      enabled: true
      hosts:
        - host: "folio-opensearch-dashboards.${var.root_domain}"
          paths:
            - path: /
              backend:
                servicePort: 5601
      annotations:
        kubernetes.io/ingress.class: alb
        alb.ingress.kubernetes.io/scheme: internet-facing
        alb.ingress.kubernetes.io/listen-ports: '[{"HTTPS":443}]'
        alb.ingress.kubernetes.io/success-codes: 200-399
        alb.ingress.kubernetes.io/group.name: ${var.rancher_cluster_name}
  EOF
  ]
}
