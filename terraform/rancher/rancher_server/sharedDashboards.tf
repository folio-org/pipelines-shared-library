data "aws_ssm_parameter" "kafka_parameters" {
  name = var.kafka_shared_name
  ignore_errors = true
}

data "aws_ssm_parameter" "opensearch_parameters" {
  name = var.opensearch_shared_name
  ignore_errors = true
}

locals {
  create_kafka_ui = can(data.aws_ssm_parameter.kafka_parameters)
  create_opensearch_dashboard = can(data.aws_ssm_parameter.opensearch_parameters)

  kafka_parameter = nonsensitive(jsondecode(data.aws_ssm_parameter.kafka_parameters.value))
  opensearch_parameter = nonsensitive(jsondecode(data.aws_ssm_parameter.opensearch_parameters.value))

  # kafka_parameter = jsondecode(coalesce(data.aws_ssm_parameter.kafka_parameters.value, "{}"))
  # opensearch_parameter = jsondecode(coalesce(data.aws_ssm_parameter.opensearch_parameters.value, "{}"))
  
  # kafka_host = coalesce(local.kafka_parameters.KAFKA_HOST, "localhost")
  # kafka_port = coalesce(local.kafka_parameters.KAFKA_PORT, "9092")
  # # kafka_port = local.kafka_parameters.KAFKA_PORT
  # # kafka_host = local.kafka_parameters.KAFKA_HOST

  # opensearch_port = local.opensearch_parameters.ELASTICSEARCH_PORT
  # opensearch_host = local.opensearch_parameters.ELASTICSEARCH_HOST
  # opensearch_username = local.opensearch_parameters.ELASTICSEARCH_USERNAME
  # opensearch_password = local.opensearch_parameters.ELASTICSEARCH_PASSWORD
}

# Only create Kafka UI if data.aws_ssm_parameter.kafka_parameters exists
resource "helm_release" "kafka-ui" {
  count            = local.create_kafka_ui ? 1 : 0
  name             = "kafka-ui"
  repository       = "https://provectus.github.io/kafka-ui"
  chart            = "kafka-ui"
  version          = "0.6.1"
  namespace        = "shared-dashboards"
  create_namespace = true
  values           = [<<EOF
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

# Only create Opensearch Dashboard if data.aws_ssm_parameter.opensearch_parameters exists
resource "helm_release" "opensearch-dashboards" {
  count            = local.create_opensearch_dashboard ? 1 : 0
  name             = "opensearch-dashboards"
  repository       = "https://opensearch-project.github.io/helm-charts"
  chart            = "opensearch-dashboards"
  version          = "2.9.2"
  namespace        = "shared-dashboards"
  create_namespace = true
  values           = [<<EOF
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
      annotations:
        kubernetes.io/ingress.class: alb
        alb.ingress.kubernetes.io/scheme: internet-facing
        alb.ingress.kubernetes.io/listen-ports: '[{"HTTPS":443}]'
        alb.ingress.kubernetes.io/success-codes: 200-399
  EOF 
  ]
}
