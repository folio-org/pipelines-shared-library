#Creating a new project in Rancher.
resource "rancher2_project" "logging" {
  depends_on = [module.eks_cluster.eks_managed_node_groups]
  count      = var.register_in_rancher && var.enable_logging ? 1 : 0
  provider   = rancher2
  name       = "logging"
  cluster_id = rancher2_cluster_sync.this[0].id
  # enable_project_monitoring = false
  container_resource_limit {
    limits_memory   = "1024Mi"
    requests_memory = "256Mi"
  }
}

# Create a new rancher2 Namespace assigned to cluster project
resource "rancher2_namespace" "logging" {
  depends_on  = [module.eks_cluster.eks_managed_node_groups]
  count       = var.register_in_rancher && var.enable_logging ? 1 : 0
  name        = "logging"
  project_id  = rancher2_project.logging[0].id
  description = "Project logging namespace"
  container_resource_limit {
    limits_memory   = "1024Mi"
    requests_memory = "256Mi"
  }
}

# Create Cognito user pool and client for authentication
resource "aws_cognito_user_pool" "kibana_user_pool" {
  name = "${module.eks_cluster.cluster_name}-kibana-user-pool"

  password_policy {
    minimum_length                   = 8
    temporary_password_validity_days = 7
  }
}

resource "aws_cognito_user_pool_client" "kibana_userpool_client" {
  name                                 = "${module.eks_cluster.cluster_name}-kibana"
  user_pool_id                         = aws_cognito_user_pool.kibana_user_pool.id
  generate_secret                      = true
  callback_urls                        = ["https://${module.eks_cluster.cluster_name}-kibana.${var.root_domain}/oauth2/idpresponse"]
  allowed_oauth_flows_user_pool_client = true
  allowed_oauth_flows                  = ["code"]
  allowed_oauth_scopes                 = ["openid"]
  supported_identity_providers         = ["COGNITO"]
}

resource "aws_cognito_user_pool_domain" "kibana_cognito_domain" {
  domain       = "${module.eks_cluster.cluster_name}-kibana"
  user_pool_id = aws_cognito_user_pool.kibana_user_pool.id
}

# Create rancher2 OpenSearch app in logging namespace
resource "rancher2_app_v2" "opensearch" {
  depends_on = [
    module.eks_cluster.eks_managed_node_groups,
    rancher2_catalog_v2.opensearch,
    time_sleep.catalog_propagation
  ]
  count         = var.register_in_rancher && var.enable_logging ? 1 : 0
  cluster_id    = rancher2_cluster_sync.this[0].cluster_id
  namespace     = rancher2_namespace.logging[0].name
  name          = "opensearch"
  repo_name     = rancher2_catalog_v2.opensearch[0].name
  chart_name    = "opensearch"
  chart_version = "2.26.0" # Latest stable version
  values        = <<-EOT
    image:
      repository: "732722833398.dkr.ecr.us-west-2.amazonaws.com/opensearch"
      tag: "2.18.0"
    opensearchJavaOpts: "-Xmx2g -Xms2g"
    
    # Disable SSL/TLS for OpenSearch
    config:
      opensearch.yml: |
        cluster.name: opensearch-cluster
        network.host: 0.0.0.0
        plugins.security.disabled: true
        plugins.security.ssl.transport.enforce_hostname_verification: false
        plugins.security.ssl.http.enabled: false
        plugins.security.ssl.transport.enabled: false
        discovery.type: single-node
        http.port: 9200
        http.host: 0.0.0.0
        # Ensure no authentication is required
        http.cors.enabled: true
        http.cors.allow-origin: "*"
        http.cors.allow-headers: "*"
    
    singleNode: true
    
    resources:
      requests:
        memory: "2Gi"
        cpu: "500m"
      limits:
        memory: "3Gi"
        cpu: "1000m"
    persistence:
      size: 100Gi
    service:
      type: NodePort
    ingress:
      enabled: true
      ingressClassName: alb
      hosts:
        - "${module.eks_cluster.cluster_name}-opensearch.${var.root_domain}"
      pathType: Prefix
      serviceName: opensearch-cluster-master
      servicePort: 9200
      annotations:
        kubernetes.io/ingress.class: alb
        alb.ingress.kubernetes.io/scheme: internet-facing
        alb.ingress.kubernetes.io/group.name: ${module.eks_cluster.cluster_name}
        alb.ingress.kubernetes.io/listen-ports: '[{"HTTPS":443}]'
        alb.ingress.kubernetes.io/success-codes: 200-399
        alb.ingress.kubernetes.io/healthcheck-path: /
        alb.ingress.kubernetes.io/load-balancer-attributes: idle_timeout.timeout_seconds=4000
  EOT
}

# Create rancher2 OpenSearch Dashboards app in logging namespace
resource "rancher2_app_v2" "opensearch_dashboards" {
  depends_on = [
    module.eks_cluster.eks_managed_node_groups,
    rancher2_catalog_v2.opensearch,
    rancher2_app_v2.opensearch
  ]
  count         = var.register_in_rancher && var.enable_logging ? 1 : 0
  cluster_id    = rancher2_cluster_sync.this[0].cluster_id
  namespace     = rancher2_namespace.logging[0].name
  name          = "opensearch-dashboards"
  repo_name     = rancher2_catalog_v2.opensearch[0].name
  chart_name    = "opensearch-dashboards"
  chart_version = "2.24.0"
  values        = <<-EOT
    image:
      repository: "732722833398.dkr.ecr.us-west-2.amazonaws.com/opensearch-dashboards"
      tag: "2.18.0"
    opensearchHosts: "http://opensearch-cluster-master:9200"
    
    # Disable SSL/TLS for OpenSearch Dashboards and configure for ALB auth
    config:
      opensearch_dashboards.yml: |
        server.host: 0.0.0.0
        opensearch.hosts: ["http://opensearch-cluster-master:9200"]
        opensearch.ssl.verificationMode: none
        opensearch.requestHeadersWhitelist: ["authorization", "securitytenant", "x-amzn-oidc-accesstoken", "x-amzn-oidc-identity", "x-amzn-oidc-data"]
        server.rewriteBasePath: false
    
    resources:
      requests:
        memory: 768Mi
        cpu: 200m
      limits:
        memory: 1024Mi
        cpu: 500m
    service:
      type: NodePort
    ingress:
      enabled: true
      ingressClassName: alb
      hosts:
        - host: "${module.eks_cluster.cluster_name}-kibana.${var.root_domain}"
          paths:
            - path: /
              pathType: Prefix
              backend:
                serviceName: opensearch-dashboards
                servicePort: 5601
      annotations:
        kubernetes.io/ingress.class: alb
        alb.ingress.kubernetes.io/scheme: internet-facing
        alb.ingress.kubernetes.io/group.name: ${module.eks_cluster.cluster_name}
        alb.ingress.kubernetes.io/listen-ports: '[{"HTTPS":443}]'
        alb.ingress.kubernetes.io/success-codes: 200-399
        alb.ingress.kubernetes.io/healthcheck-path: /
        alb.ingress.kubernetes.io/load-balancer-attributes: idle_timeout.timeout_seconds=4000
        alb.ingress.kubernetes.io/auth-idp-cognito: '{"UserPoolArn":"${aws_cognito_user_pool.kibana_user_pool.arn}","UserPoolClientId":"${aws_cognito_user_pool_client.kibana_userpool_client.id}", "UserPoolDomain":"${module.eks_cluster.cluster_name}-kibana"}'
        alb.ingress.kubernetes.io/auth-on-unauthenticated-request: authenticate
        alb.ingress.kubernetes.io/auth-scope: openid
        alb.ingress.kubernetes.io/auth-session-cookie: AWSELBAuthSessionCookie
        alb.ingress.kubernetes.io/auth-session-timeout: "3600"
        alb.ingress.kubernetes.io/auth-type: cognito
  EOT
}

# Create OpenSearch output manifest
resource "kubectl_manifest" "opensearch_output" {
  depends_on         = [module.eks_cluster.eks_managed_node_groups]
  count              = var.register_in_rancher && var.enable_logging ? 1 : 0
  provider           = kubectl
  override_namespace = rancher2_namespace.logging[0].name
  yaml_body          = <<YAML
apiVersion: v1
kind: ConfigMap
metadata:
  name: opensearch-output
data:
  fluentd.conf: |

    # Ignore fluentd own events
    <label @FLUENT_LOG>
      <match fluent.**>
        @type null
        @id ignore_fluent_logs
      </match>
    </label>

    # TCP input to receive logs from the forwarders
    <source>
      @type forward
      bind 0.0.0.0
      port 24224
    </source>

    # HTTP input for the liveness and readiness probes
    <source>
      @type http
      bind 0.0.0.0
      port 9880
    </source>

    # Throw the healthcheck to the standard output instead of forwarding it
    <match fluentd.healthcheck>
      @type stdout
    </match>

    # Send the logs to OpenSearch
    <match **>
      @type opensearch
      include_tag_key true
      host "#{ENV['OPENSEARCH_HOST']}"
      port "#{ENV['OPENSEARCH_PORT']}"
      scheme http
      logstash_format true
      suppress_type_name true
      reconnect_on_error true
      reload_on_failure true
      reload_connections false
      request_timeout 120s
      logstash_prefix logstash-$${record["kubernetes"]["namespace_name"]}
      <buffer>
        @type file
        retry_forever false
        retry_max_times 3
        retry_wait 10
        retry_max_interval 300
        path /opt/bitnami/fluentd/logs/buffers/logs.buffer
        flush_thread_count 4
        flush_interval 15s
        chunk_limit_size 80M
      </buffer>
    </match>
  YAML
}

# Create fluentd ServiceAccount
resource "kubectl_manifest" "fluentd_serviceaccount" {
  depends_on = [
    module.eks_cluster.eks_managed_node_groups,
    rancher2_app_v2.opensearch
  ]
  count              = var.register_in_rancher && var.enable_logging ? 1 : 0
  provider           = kubectl
  override_namespace = rancher2_namespace.logging[0].name
  yaml_body          = <<YAML
apiVersion: v1
kind: ServiceAccount
metadata:
  name: fluentd
  namespace: logging
  YAML
}

# Create fluentd ClusterRole
resource "kubectl_manifest" "fluentd_clusterrole" {
  depends_on = [module.eks_cluster.eks_managed_node_groups]
  count      = var.register_in_rancher && var.enable_logging ? 1 : 0
  provider   = kubectl
  yaml_body  = <<YAML
apiVersion: rbac.authorization.k8s.io/v1
kind: ClusterRole
metadata:
  name: fluentd
rules:
- apiGroups:
  - ""
  resources:
  - pods
  - namespaces
  verbs:
  - get
  - list
  - watch
  YAML
}

# Create fluentd ClusterRoleBinding
resource "kubectl_manifest" "fluentd_clusterrolebinding" {
  depends_on = [
    kubectl_manifest.fluentd_serviceaccount,
    kubectl_manifest.fluentd_clusterrole
  ]
  count     = var.register_in_rancher && var.enable_logging ? 1 : 0
  provider  = kubectl
  yaml_body = <<YAML
apiVersion: rbac.authorization.k8s.io/v1
kind: ClusterRoleBinding
metadata:
  name: fluentd
roleRef:
  kind: ClusterRole
  name: fluentd
  apiGroup: rbac.authorization.k8s.io
subjects:
- kind: ServiceAccount
  name: fluentd
  namespace: logging
  YAML
}

# Create fluentd DaemonSet directly via kubectl
resource "kubectl_manifest" "fluentd_daemonset" {
  depends_on = [
    module.eks_cluster.eks_managed_node_groups,
    rancher2_app_v2.opensearch,
    kubectl_manifest.opensearch_output,
    kubectl_manifest.fluentd_serviceaccount,
    kubectl_manifest.fluentd_clusterrolebinding
  ]
  count              = var.register_in_rancher && var.enable_logging ? 1 : 0
  provider           = kubectl
  override_namespace = rancher2_namespace.logging[0].name
  yaml_body          = <<YAML
apiVersion: apps/v1
kind: DaemonSet
metadata:
  name: fluentd
  labels:
    app: fluentd
spec:
  selector:
    matchLabels:
      app: fluentd
  template:
    metadata:
      labels:
        app: fluentd
    spec:
      serviceAccountName: fluentd
      containers:
      - name: fluentd
        image: 732722833398.dkr.ecr.us-west-2.amazonaws.com/fluentd:1.16.0-debian-11-r1
        env:
        - name: OPENSEARCH_HOST
          value: "opensearch-cluster-master"
        - name: OPENSEARCH_PORT
          value: "9200"
        resources:
          limits:
            memory: 512Mi
          requests:
            cpu: 100m
            memory: 200Mi
        volumeMounts:
        - name: varlog
          mountPath: /var/log
        - name: varlibdockercontainers
          mountPath: /var/lib/docker/containers
          readOnly: true
        - name: config
          mountPath: /fluentd/etc/fluent.conf
          subPath: fluent.conf
      terminationGracePeriodSeconds: 30
      volumes:
      - name: varlog
        hostPath:
          path: /var/log
      - name: varlibdockercontainers
        hostPath:
          path: /var/lib/docker/containers
      - name: config
        configMap:
          name: opensearch-output
  YAML
}
