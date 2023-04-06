data "aws_ssm_parameter" "kafka_parameter" {
  name = "folio-kafka"
  ignore_errors = true
}

data "aws_ssm_parameter" "opensearch_parameter" {
  name = "folio-opensearch"
  ignore_errors = true
}

output "my_parameter_value" {
  value = data.aws_ssm_parameter.my_parameter.value
}

locals {
  kafka_parameters = jsondecode(data.aws_ssm_parameter.kafka_parameter.value)
  opensearch_parameters = jsondecode(data.aws_ssm_parameter.opensearch_parameter.value)

  kafka_port = local.kafka_parameters.KAFKA_PORT
  kafka_host = local.kafka_parameters.KAFKA_HOST

  opensearch_port = local.opensearch_parameters.ELASTICSEARCH_PORT
  opensearch_host = local.opensearch_parameters.ELASTICSEARCH_HOST
  opensearch_username = local.opensearch_parameters.ELASTICSEARCH_USERNAME
  opensearch_password = local.opensearch_parameters.ELASTICSEARCH_PASSWORD
}

# Install Kafka UI
resource "helm_release" "kafka-ui" {
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
            bootstrapServers: ${var.kafka_embedded ? "kafka-${var.rancher_project_name}" : element(split(":", aws_msk_cluster.this[0].bootstrap_brokers), 0)}:9092
      auth:
        type: disabled
      management:
        health:
          ldap:
            enabled: false
  EOF 
  ]
}

# Install Opensearch dashboard
resource "helm_release" "opensearch-dashboards" {
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
    opensearchHosts: ${var.es_embedded ? "http://opensearch-${var.rancher_project_name}:9200" : "https://${module.aws_es[0].endpoint}:443"}
    extraEnvs:
      - name: DISABLE_SECURITY_DASHBOARDS_PLUGIN
        value: "true"
      - name: OPENSEARCH_SSL_VERIFICATIONMODE
        value: "full"
      - name: OPENSEARCH_USERNAME
        value: ${var.es_embedded ? "admin" : var.es_username}
      - name: OPENSEARCH_PASSWORD
        value: ${var.es_embedded ? "admin" : random_password.es_password[0].result}
    resources:
      requests:
        memory: 2048Mi
      limits:
        memory: 3072Mi
    ingress:
      enabled: true
      hosts:
        - host: "folio-opensearch.${var.root_domain}"
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
