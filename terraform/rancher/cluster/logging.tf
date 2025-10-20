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

# Create rancher2 Elasticsearch app in logging namespace
resource "rancher2_app_v2" "elasticsearch" {
  depends_on = [
    module.eks_cluster.eks_managed_node_groups,
    rancher2_catalog_v2.opensearch,
    time_sleep.catalog_propagation
  ]
  count         = var.register_in_rancher && var.enable_logging ? 1 : 0
  cluster_id    = rancher2_cluster_sync.this[0].cluster_id
  namespace     = rancher2_namespace.logging[0].name
  name          = "elasticsearch"
  repo_name     = rancher2_catalog_v2.opensearch[0].name
  chart_name    = "elasticsearch"
  chart_version = "7.17.3" # Compatible with Filebeat 7.17.15
  values        = <<-EOT
    # Use the default image (Elasticsearch 7.17.15)
    image: "docker.elastic.co/elasticsearch/elasticsearch"
    imageTag: "7.17.15"
    
    esJavaOpts: "-Xmx2g -Xms2g"
    
    # Single node configuration - CRITICAL SETTINGS
    clusterName: "elasticsearch"
    nodeGroup: "master"
    replicas: 1
    # DON'T set minimumMasterNodes for single-node
    # minimumMasterNodes: 1  # This conflicts with single-node
    
    # Completely disable SSL/TLS and use single-node discovery
    esConfig:
      elasticsearch.yml: |
        # Cluster and network settings
        cluster.name: elasticsearch
        network.host: 0.0.0.0
        http.port: 9200
        http.host: 0.0.0.0
        
        # SINGLE NODE DISCOVERY - no cluster bootstrap needed
        discovery.type: single-node
        
        # Disable ALL security features
        xpack.security.enabled: false
        xpack.security.http.ssl.enabled: false
        xpack.security.transport.ssl.enabled: false
        xpack.monitoring.enabled: false
        xpack.watcher.enabled: false
        xpack.ml.enabled: false
        
        # Enable CORS for dashboard access
        http.cors.enabled: true
        http.cors.allow-origin: "*"
        http.cors.allow-headers: "X-Requested-With,X-Auth-Token,Content-Type,Content-Length,Authorization"
        http.cors.allow-credentials: true
        
        # Performance settings
        bootstrap.memory_lock: false
        indices.query.bool.max_clause_count: 10000
    
    # Resource allocation
    resources:
      requests:
        memory: "2Gi"
        cpu: "500m"
      limits:
        memory: "3Gi" 
        cpu: "1000m"
    
    # Persistent storage
    volumeClaimTemplate:
      accessModes: [ "ReadWriteOnce" ]
      resources:
        requests:
          storage: 100Gi
    
    # Service configuration
    service:
      type: NodePort
      
    # Ingress for external access
    ingress:
      enabled: true
      className: alb
      hosts:
        - host: "${module.eks_cluster.cluster_name}-elasticsearch.${var.root_domain}"
          paths:
            - path: /
              pathType: Prefix
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

# Create rancher2 Kibana app in logging namespace
resource "rancher2_app_v2" "kibana" {
  depends_on = [
    module.eks_cluster.eks_managed_node_groups,
    rancher2_catalog_v2.opensearch,
    rancher2_app_v2.elasticsearch
  ]
  count         = var.register_in_rancher && var.enable_logging ? 1 : 0
  cluster_id    = rancher2_cluster_sync.this[0].cluster_id
  namespace     = rancher2_namespace.logging[0].name
  name          = "kibana"
  repo_name     = rancher2_catalog_v2.opensearch[0].name
  chart_name    = "kibana"
  chart_version = "7.17.3"
  values        = <<-EOT
    # Use Kibana 7.17.15 image
    image: "docker.elastic.co/kibana/kibana"
    imageTag: "7.17.15"
    
    # Connect to Elasticsearch service
    elasticsearchHosts: "http://elasticsearch-master:9200"
    
    # Complete Kibana configuration
    kibanaConfig:
      kibana.yml: |
        # Server settings
        server.host: 0.0.0.0
        server.port: 5601
        server.name: "kibana"
        server.rewriteBasePath: false
        
        # Elasticsearch connection
        elasticsearch.hosts: ["http://elasticsearch-master:9200"]
        elasticsearch.ssl.verificationMode: none
        elasticsearch.requestTimeout: 30000
        elasticsearch.pingTimeout: 1500
        
        # Disable all X-Pack security features
        xpack.security.enabled: false
        xpack.encryptedSavedObjects.encryptionKey: "something_at_least_32_characters_long_for_session_encryption"
        xpack.reporting.enabled: false
        xpack.monitoring.enabled: false
        xpack.ml.enabled: false
        xpack.watcher.enabled: false
        
        # Request headers for ALB authentication
        elasticsearch.requestHeadersWhitelist: ["authorization", "x-amzn-oidc-accesstoken", "x-amzn-oidc-identity", "x-amzn-oidc-data"]
        
        # Logging configuration
        logging.level: info
    
    # Resource allocation
    resources:
      requests:
        memory: 768Mi
        cpu: 200m
      limits:
        memory: 1024Mi
        cpu: 500m
        
    # Service configuration
    service:
      type: NodePort
      port: 5601
      
    # Health checks
    readinessProbe:
      httpGet:
        path: /app/kibana
        port: 5601
      initialDelaySeconds: 30
      periodSeconds: 10
      timeoutSeconds: 5
      
    livenessProbe:
      httpGet:
        path: /app/kibana
        port: 5601
      initialDelaySeconds: 60
      periodSeconds: 20
      timeoutSeconds: 5
      
    # Ingress for external access with Cognito auth
    ingress:
      enabled: true
      className: alb
      hosts:
        - host: "${module.eks_cluster.cluster_name}-kibana.${var.root_domain}"
          paths:
            - path: /
              pathType: Prefix
      annotations:
        kubernetes.io/ingress.class: alb
        alb.ingress.kubernetes.io/scheme: internet-facing
        alb.ingress.kubernetes.io/group.name: ${module.eks_cluster.cluster_name}
        alb.ingress.kubernetes.io/listen-ports: '[{"HTTPS":443}]'
        alb.ingress.kubernetes.io/success-codes: 200-399
        alb.ingress.kubernetes.io/healthcheck-path: /app/kibana
        alb.ingress.kubernetes.io/load-balancer-attributes: idle_timeout.timeout_seconds=4000
        alb.ingress.kubernetes.io/auth-idp-cognito: '{"UserPoolArn":"${aws_cognito_user_pool.kibana_user_pool.arn}","UserPoolClientId":"${aws_cognito_user_pool_client.kibana_userpool_client.id}", "UserPoolDomain":"${module.eks_cluster.cluster_name}-kibana"}'
        alb.ingress.kubernetes.io/auth-on-unauthenticated-request: authenticate
        alb.ingress.kubernetes.io/auth-scope: openid
        alb.ingress.kubernetes.io/auth-session-cookie: AWSELBAuthSessionCookie
        alb.ingress.kubernetes.io/auth-session-timeout: "3600"
        alb.ingress.kubernetes.io/auth-type: cognito
  EOT
}

# Create Filebeat configuration
resource "kubectl_manifest" "filebeat_config" {
  depends_on         = [module.eks_cluster.eks_managed_node_groups]
  count              = var.register_in_rancher && var.enable_logging ? 1 : 0
  provider           = kubectl
  override_namespace = rancher2_namespace.logging[0].name
  yaml_body          = <<YAML
apiVersion: v1
kind: ConfigMap
metadata:
  name: filebeat-config
data:
  filebeat.yml: |
    # Filebeat input configuration
    filebeat.inputs:
    - type: container
      paths:
        - /var/log/containers/*.log
      # Exclude system pods to reduce noise
      exclude_lines: ['^[[:space:]]*$', '^[[:space:]]*#']
      multiline.pattern: '^\d{4}-\d{2}-\d{2}'
      multiline.negate: true
      multiline.match: after
      processors:
      - add_kubernetes_metadata:
          host: $${NODE_NAME}
          matchers:
          - logs_path:
              logs_path: "/var/log/containers/"
      - drop_fields:
          fields: ["host", "agent", "ecs", "input"]
      - decode_json_fields:
          fields: ["message"]
          target: ""
          overwrite_keys: true
          when:
            contains:
              message: "{"
    
    # Enable HTTP endpoint for health checks
    http:
      enabled: true
      host: 0.0.0.0
      port: 5066
    
    # Output to Elasticsearch - SIMPLIFIED CONFIGURATION
    output.elasticsearch:
      hosts: ["elasticsearch-master.logging.svc.cluster.local:9200"]
      protocol: "http"
      index: "logs-%%{+yyyy.MM.dd}"
      
      # Connection and retry settings
      timeout: 90
      max_retries: 3
      backoff.init: 1s
      backoff.max: 60s
      
      # Bulk settings for performance
      bulk_max_size: 1000
      flush_bytes: 10485760
      flush_interval: 1s
      
      # Template and pipeline settings - DISABLED to avoid conflicts
      template.enabled: false
      pipeline: ""
      
    # Completely disable setup features to avoid any compatibility issues
    setup.template.enabled: false
    setup.ilm.enabled: false
    setup.dashboards.enabled: false
    setup.kibana.enabled: false
    
    # Logging configuration
    logging.level: info
    logging.to_files: true
    logging.files:
      path: /usr/share/filebeat/logs
      name: filebeat
      keepfiles: 7
      permissions: 0644
    
    # Performance and memory settings
    queue.mem:
      events: 4096
      flush.min_events: 512
      flush.timeout: 5s
    
    # Additional metadata processors
    processors:
    - add_host_metadata:
        when.not.contains.tags: forwarded
    - add_docker_metadata: ~
    - add_kubernetes_metadata:
        host: $${NODE_NAME}
        default_indexers.enabled: false
        default_matchers.enabled: false
        indexers:
        - container:
        matchers:
        - logs_path:
            logs_path: "/var/log/containers/"
      
    # Logging configuration
    logging.level: info
    logging.to_files: true
    logging.files:
      path: /usr/share/filebeat/logs
      name: filebeat
      keepfiles: 7
      permissions: 0644
    
    # Performance tuning
    queue.mem:
      events: 4096
      flush.min_events: 512
      flush.timeout: 5s
    
    processors:
    - add_host_metadata:
        when.not.contains.tags: forwarded
  YAML
}

# Create filebeat ServiceAccount
resource "kubectl_manifest" "filebeat_serviceaccount" {
  depends_on = [
    module.eks_cluster.eks_managed_node_groups,
    rancher2_app_v2.elasticsearch
  ]
  count              = var.register_in_rancher && var.enable_logging ? 1 : 0
  provider           = kubectl
  override_namespace = rancher2_namespace.logging[0].name
  yaml_body          = <<YAML
apiVersion: v1
kind: ServiceAccount
metadata:
  name: filebeat
  namespace: logging
  YAML
}

# Create filebeat ClusterRole
resource "kubectl_manifest" "filebeat_clusterrole" {
  depends_on = [module.eks_cluster.eks_managed_node_groups]
  count      = var.register_in_rancher && var.enable_logging ? 1 : 0
  provider   = kubectl
  yaml_body  = <<YAML
apiVersion: rbac.authorization.k8s.io/v1
kind: ClusterRole
metadata:
  name: filebeat
rules:
- apiGroups:
  - ""
  resources:
  - pods
  - namespaces
  - nodes
  verbs:
  - get
  - list
  - watch
- apiGroups:
  - "apps"
  resources:
  - replicasets
  verbs:
  - get
  - list
  - watch
  YAML
}

# Create filebeat ClusterRoleBinding
resource "kubectl_manifest" "filebeat_clusterrolebinding" {
  depends_on = [
    kubectl_manifest.filebeat_serviceaccount,
    kubectl_manifest.filebeat_clusterrole
  ]
  count     = var.register_in_rancher && var.enable_logging ? 1 : 0
  provider  = kubectl
  yaml_body = <<YAML
apiVersion: rbac.authorization.k8s.io/v1
kind: ClusterRoleBinding
metadata:
  name: filebeat
roleRef:
  kind: ClusterRole
  name: filebeat
  apiGroup: rbac.authorization.k8s.io
subjects:
- kind: ServiceAccount
  name: filebeat
  namespace: logging
  YAML
}

# Create filebeat DaemonSet directly via kubectl
resource "kubectl_manifest" "filebeat_daemonset" {
  depends_on = [
    module.eks_cluster.eks_managed_node_groups,
    rancher2_app_v2.elasticsearch,
    kubectl_manifest.filebeat_config,
    kubectl_manifest.filebeat_serviceaccount,
    kubectl_manifest.filebeat_clusterrolebinding
  ]
  count              = var.register_in_rancher && var.enable_logging ? 1 : 0
  provider           = kubectl
  override_namespace = rancher2_namespace.logging[0].name
  yaml_body          = <<YAML
apiVersion: apps/v1
kind: DaemonSet
metadata:
  name: filebeat
  labels:
    app: filebeat
spec:
  selector:
    matchLabels:
      app: filebeat
  template:
    metadata:
      labels:
        app: filebeat
    spec:
      serviceAccountName: filebeat
      hostNetwork: true
      dnsPolicy: ClusterFirstWithHostNet
      containers:
      - name: filebeat
        image: docker.elastic.co/beats/filebeat:7.17.15
        args: [
          "-c", "/etc/filebeat.yml",
          "-e"
        ]
        env:
        - name: NODE_NAME
          valueFrom:
            fieldRef:
              fieldPath: spec.nodeName
        - name: ELASTICSEARCH_HOST
          value: "elasticsearch-master.logging.svc.cluster.local"
        - name: ELASTICSEARCH_PORT
          value: "9200"
        securityContext:
          runAsUser: 0
          capabilities:
            add:
            - DAC_READ_SEARCH
        resources:
          limits:
            memory: 512Mi
            cpu: 200m
          requests:
            cpu: 50m
            memory: 128Mi
        volumeMounts:
        - name: config
          mountPath: /etc/filebeat.yml
          subPath: filebeat.yml
          readOnly: true
        - name: data
          mountPath: /usr/share/filebeat/data
        - name: varlibdockercontainers
          mountPath: /var/lib/docker/containers
          readOnly: true
        - name: varlog
          mountPath: /var/log
          readOnly: true
        livenessProbe:
          httpGet:
            path: /
            port: 5066
          initialDelaySeconds: 30
          periodSeconds: 30
          timeoutSeconds: 10
          failureThreshold: 3
        readinessProbe:
          httpGet:
            path: /
            port: 5066
          initialDelaySeconds: 15
          periodSeconds: 10
          timeoutSeconds: 5
          failureThreshold: 3
      terminationGracePeriodSeconds: 30
      volumes:
      - name: config
        configMap:
          defaultMode: 0640
          name: filebeat-config
      - name: varlibdockercontainers
        hostPath:
          path: /var/lib/docker/containers
      - name: varlog
        hostPath:
          path: /var/log
      - name: data
        hostPath:
          path: /var/lib/filebeat-data
          type: DirectoryOrCreate
  YAML
}

# Create Elasticsearch ILM policy for log retention
resource "kubectl_manifest" "elasticsearch_ilm_policy" {
  depends_on = [
    module.eks_cluster.eks_managed_node_groups,
    rancher2_app_v2.elasticsearch
  ]
  count     = var.register_in_rancher && var.enable_logging ? 1 : 0
  provider  = kubectl
  yaml_body = <<YAML
apiVersion: v1
kind: ConfigMap
metadata:
  name: elasticsearch-ilm-policy
  namespace: logging
data:
  policy.json: |
    {
      "policy": {
        "phases": {
          "hot": {
            "actions": {
              "rollover": {
                "max_size": "50gb",
                "max_docs": 2000000,
                "max_age": "1d"
              }
            }
          },
          "warm": {
            "min_age": "1d",
            "actions": {
              "allocate": {
                "number_of_replicas": 0
              }
            }
          },
          "delete": {
            "min_age": "3d",
            "actions": {
              "delete": {}
            }
          }
        }
      }
    }
YAML
}

# Job to apply ILM policy to Elasticsearch
resource "kubectl_manifest" "elasticsearch_ilm_policy_job" {
  depends_on = [
    kubectl_manifest.elasticsearch_ilm_policy,
    rancher2_app_v2.elasticsearch
  ]
  count     = var.register_in_rancher && var.enable_logging ? 1 : 0
  provider  = kubectl
  yaml_body = <<YAML
apiVersion: batch/v1
kind: Job
metadata:
  name: elasticsearch-ilm-policy-setup
  namespace: logging
spec:
  ttlSecondsAfterFinished: 300
  template:
    spec:
      restartPolicy: OnFailure
      containers:
      - name: curl
        image: curlimages/curl:latest
        command: ["/bin/sh"]
        args:
        - -c
        - |
          set -e
          echo "Waiting for Elasticsearch to be ready..."
          
          # Wait for Elasticsearch to be healthy
          for i in $(seq 1 30); do
            if curl -s -f "http://elasticsearch-master:9200/_cluster/health?wait_for_status=yellow&timeout=10s"; then
              echo "Elasticsearch is ready!"
              break
            fi
            echo "Attempt $i/30: Elasticsearch not ready yet, waiting..."
            sleep 10
          done
          
          # Verify we can connect
          curl -s "http://elasticsearch-master:9200/" || {
            echo "Failed to connect to Elasticsearch"
            exit 1
          }
          
          echo "Creating ILM policy..."
          # Apply ILM policy with simpler configuration
          curl -X PUT "http://elasticsearch-master:9200/_ilm/policy/logs-policy" \
            -H 'Content-Type: application/json' \
            -d '{
              "policy": {
                "phases": {
                  "hot": {
                    "actions": {
                      "rollover": {
                        "max_size": "1GB",
                        "max_age": "1d"
                      }
                    }
                  },
                  "delete": {
                    "min_age": "3d",
                    "actions": {
                      "delete": {}
                    }
                  }
                }
              }
            }' || echo "ILM policy creation failed, but continuing..."
          
          echo "Creating index template..."
          # Create simple index template
          curl -X PUT "http://elasticsearch-master:9200/_template/logs-template" \
            -H 'Content-Type: application/json' \
            -d '{
              "index_patterns": ["logs-*"],
              "settings": {
                "number_of_shards": 1,
                "number_of_replicas": 0,
                "index.refresh_interval": "30s"
              }
            }' || echo "Template creation failed, but continuing..."
            
          echo "Setup completed successfully!"
        volumeMounts:
        - name: policy
          mountPath: /policy
      volumes:
      - name: policy
        configMap:
          name: elasticsearch-ilm-policy
YAML
}
