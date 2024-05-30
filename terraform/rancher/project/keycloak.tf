resource "helm_release" "keycloak" {
  count      = (var.eureka ? 1 : 0)
  chart      = "keycloak"
  depends_on = [rancher2_secret.keycloak-credentials, helm_release.postgresql]
  name       = "keycloak-${var.rancher_project_name}"
  namespace  = rancher2_namespace.this.id
  version    = "20.0.1"
  repository = "https://repository.folio.org/repository/helm-bitnami-proxy"
  values = [
    <<-EOF
      image:
        registry: 732722833398.dkr.ecr.us-west-2.amazonaws.com
        repository: keycloak
        tag: latest
        pullPolicy: IfNotPresent
        debug: true
      enableDefaultInitContainers: false
      replicaCount: 1
      containerSecurityContext:
        enabled: false
        seLinuxOptions: {}
        runAsUser: 1001
        runAsGroup: 1001
        runAsNonRoot: true
        privileged: false
        readOnlyRootFilesystem: true
        allowPrivilegeEscalation: false
        capabilities:
          drop: ["ALL"]
        seccompProfile:
          type: "RuntimeDefault"
      database: postgresql
      postgresql:
        enabled: false
      externalDatabase:
        existingSecret: keycloak-credentials
        existingSecretHostKey: KEYCLOAK_PG_HOST
        existingSecretPortKey: KEYCLOAK_PG_PORT
        existingSecretUserKey: KEYCLOAK_PG_USER
        existingSecretDatabaseKey: KEYCLOAK_DATABASE
        existingSecretPasswordKey: KEYCLOAK_PG_PASSWORD
      networkPolicy:
        enabled: false
      service:
        type: NodePort
        http:
          enabled: true
      ingress:
        enabled: true
        hostname: ${local.keycloak_url}
        ingressClassName: ""
        pathType: ImplementationSpecific
        path: /*
        annotations:
          kubernetes.io/ingress.class: alb
          alb.ingress.kubernetes.io/scheme: internet-facing
          alb.ingress.kubernetes.io/group.name: ${local.group_name}
          alb.ingress.kubernetes.io/listen-ports: '[{"HTTPS":443}]'
          alb.ingress.kubernetes.io/success-codes: 200-399
          alb.ingress.kubernetes.io/healthcheck-path: /health/ready
          alb.ingress.kubernetes.io/healthcheck-port: '80'
      auth:
        adminUser: "kc_admin"
        existingSecret: keycloak-credentials
        passwordSecretKey: KEYCLOAK_ADMIN_PASSWORD
      extraEnvVars:
        - name: KC_FOLIO_BE_ADMIN_CLIENT_SECRET
          valueFrom:
            secretKeyRef:
              name: keycloak-credentials
              key: KEYCLOAK_FOLIO_BE_ADMIN_CLIENT_SECRET
        - name: KC_HTTPS_KEY_STORE_PASSWORD
          valueFrom:
            secretKeyRef:
              name: keycloak-credentials
              key: KEYCLOAK_HTTPS_KEY_STORE_PASSWORD
        - name: KEYCLOAK_LOG_LEVEL
          value: DEBUG
        - name: KC_HOSTNAME_STRICT
          value: true
        - name: KC_HOSTNAME
          value: ${local.keycloak_url}
      livenessProbe:
        enabled: false
      readinessProbe:
        enabled: false
      startupProbe:
        enabled: false
    EOF
  ]
}
