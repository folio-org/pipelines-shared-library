resource "helm_release" "kong" {
  count      = var.eureka ? 1 : 0
  chart      = "kong"
  depends_on = [rancher2_secret.db-credentials]
  name       = "kong-${var.rancher_project_name}"
  namespace  = rancher2_namespace.this.id
  version    = "12.0.11"
  repository = "https://repository.folio.org/repository/helm-bitnami-proxy"
  values = [
    <<-EOF
global:
  imagePullSecrets: []
  storageClass: ""
  compatibility:
    openshift:
      adaptSecurityContext: auto
kubeVersion: ""
nameOverride: ""
fullnameOverride: ""
commonAnnotations: {}
commonLabels: {}
clusterDomain: cluster.local
extraDeploy: []
diagnosticMode:
  enabled: false
  command:
    - sleep
  ## @param diagnosticMode.args Args to override all containers in the daemonset/deployment
  ##
  args:
    - infinity
image:
  registry: docker.io
  repository: bitnami/kong
  tag: 3.6.1-debian-12-r23
  digest: ""
  ## Specify a imagePullPolicy
  ## Defaults to 'Always' if image tag is 'latest', else set to 'IfNotPresent'
  ## ref: https://kubernetes.io/docs/concepts/containers/images/#pre-pulled-images
  ##
  pullPolicy: IfNotPresent
  ## Optionally specify an array of imagePullSecrets.
  ## Secrets must be manually created in the namespace.
  ## ref: https://kubernetes.io/docs/tasks/configure-pod-container/pull-image-private-registry/
  ## E.g:
  ## pullSecrets:
  ##   - myRegistryKeySecretName
  ##
  pullSecrets: []
  ## Enable debug mode
  ##
  debug: false
## @param database Select which database backend Kong will use. Can be 'postgresql', 'cassandra' or 'off'
##
database: postgresql
## @section Kong deployment / daemonset parameters
##

## @param useDaemonset Use a daemonset instead of a deployment. `replicaCount` will not take effect.
##
useDaemonset: false
## @param replicaCount Number of Kong replicas
##
replicaCount: 2
## Kong containers' Security Context
## ref: https://kubernetes.io/docs/tasks/configure-pod-container/security-context/#set-the-security-context-for-a-container
## @param containerSecurityContext.enabled Enabled containers' Security Context
## @param containerSecurityContext.seLinuxOptions [object,nullable] Set SELinux options in container
## @param containerSecurityContext.runAsUser Set containers' Security Context runAsUser
## @param containerSecurityContext.runAsGroup Set containers' Security Context runAsGroup
## @param containerSecurityContext.runAsNonRoot Set container's Security Context runAsNonRoot
## @param containerSecurityContext.privileged Set container's Security Context privileged
## @param containerSecurityContext.readOnlyRootFilesystem Set container's Security Context readOnlyRootFilesystem
## @param containerSecurityContext.allowPrivilegeEscalation Set container's Security Context allowPrivilegeEscalation
## @param containerSecurityContext.capabilities.drop List of capabilities to be dropped
## @param containerSecurityContext.seccompProfile.type Set container's Security Context seccomp profile
##
containerSecurityContext:
  enabled: true
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
## Kong pods' Security Context
## ref: https://kubernetes.io/docs/tasks/configure-pod-container/security-context/#set-the-security-context-for-a-pod
## @param podSecurityContext.enabled Enabled Kong pods' Security Context
## @param podSecurityContext.fsGroupChangePolicy Set filesystem group change policy
## @param podSecurityContext.sysctls Set kernel settings using the sysctl interface
## @param podSecurityContext.supplementalGroups Set filesystem extra groups
## @param podSecurityContext.fsGroup Set Kong pod's Security Context fsGroup
##
podSecurityContext:
  enabled: true
  fsGroupChangePolicy: Always
  sysctls: []
  supplementalGroups: []
  fsGroup: 1001
## @param updateStrategy.type Kong update strategy
## @param updateStrategy.rollingUpdate Kong deployment rolling update configuration parameters
## ref: https://kubernetes.io/docs/concepts/workloads/controllers/deployment/#strategy
## Note: Set to Recreate if you use persistent volume that cannot be mounted by more than one pods to make sure the pods is destroyed first.
## E.g:
## updateStrategy:
##  type: RollingUpdate
##  rollingUpdate:
##    maxSurge: 25%
##    maxUnavailable: 25%
##
updateStrategy:
  type: RollingUpdate
  rollingUpdate: {}
## @param automountServiceAccountToken Mount Service Account token in pod
##
automountServiceAccountToken: true
## @param hostAliases Add deployment host aliases
## https://kubernetes.io/docs/concepts/services-networking/add-entries-to-pod-etc-hosts-with-host-aliases/
##
hostAliases: []
## @param topologySpreadConstraints Topology Spread Constraints for pod assignment spread across your cluster among failure-domains. Evaluated as a template
## Ref: https://kubernetes.io/docs/concepts/workloads/pods/pod-topology-spread-constraints/#spread-constraints-for-pods
##
topologySpreadConstraints: []
## @param priorityClassName Priority Class Name
## ref: https://kubernetes.io/docs/concepts/configuration/pod-priority-preemption/#priorityclass
##
priorityClassName: ""
## @param schedulerName Use an alternate scheduler, e.g. "stork".
## ref: https://kubernetes.io/docs/tasks/administer-cluster/configure-multiple-schedulers/
##
schedulerName: ""
## @param terminationGracePeriodSeconds Seconds Kong pod needs to terminate gracefully
## ref: https://kubernetes.io/docs/concepts/workloads/pods/pod/#termination-of-pods
##
terminationGracePeriodSeconds: ""
## @param podAnnotations Additional pod annotations
## ref: https://kubernetes.io/docs/concepts/overview/working-with-objects/annotations/
##
podAnnotations: {}
## @param podLabels Additional pod labels
## Ref: https://kubernetes.io/docs/concepts/overview/working-with-objects/labels/
##
podLabels: {}
## @param podAffinityPreset Pod affinity preset. Ignored if `affinity` is set. Allowed values: `soft` or `hard`
## ref: https://kubernetes.io/docs/concepts/scheduling-eviction/assign-pod-node/#inter-pod-affinity-and-anti-affinity
##
podAffinityPreset: ""
## @param podAntiAffinityPreset Pod anti-affinity preset. Ignored if `affinity` is set. Allowed values: `soft` or `hard`
## Ref: https://kubernetes.io/docs/concepts/scheduling-eviction/assign-pod-node/#inter-pod-affinity-and-anti-affinity
##
podAntiAffinityPreset: soft
## Node affinity preset
## Ref: https://kubernetes.io/docs/concepts/scheduling-eviction/assign-pod-node/#node-affinity
##
nodeAffinityPreset:
  ## @param nodeAffinityPreset.type Node affinity preset type. Ignored if `affinity` is set. Allowed values: `soft` or `hard`
  ##
  type: ""
  ## @param nodeAffinityPreset.key Node label key to match Ignored if `affinity` is set.
  ## E.g.
  ## key: "kubernetes.io/e2e-az-name"
  ##
  key: ""
  ## @param nodeAffinityPreset.values Node label values to match. Ignored if `affinity` is set.
  ## E.g.
  ## values:
  ##   - e2e-az1
  ##   - e2e-az2
  ##
  values: []
## @param affinity Affinity for pod assignment
## Ref: https://kubernetes.io/docs/concepts/configuration/assign-pod-node/#affinity-and-anti-affinity
## Note: `podAffinityPreset`, `podAntiAffinityPreset`, and `nodeAffinityPreset` will be ignored when it's set
##
affinity: {}
## @param nodeSelector Node labels for pod assignment
## Ref: https://kubernetes.io/docs/concepts/scheduling-eviction/assign-pod-node/
##
nodeSelector: {}
## @param tolerations Tolerations for pod assignment
## Ref: https://kubernetes.io/docs/concepts/configuration/taint-and-toleration/
##
tolerations: []
## @param extraVolumes Array of extra volumes to be added to the Kong deployment deployment (evaluated as template). Requires setting `extraVolumeMounts`
##
extraVolumes: []
## @param initContainers Add additional init containers to the Kong pods
## e.g.
## - name: your-image-name
##   image: your-image
##   imagePullPolicy: Always
##   ports:
##     - name: portname
##        containerPort: 1234
##
initContainers: []
## @param sidecars Add additional sidecar containers to the Kong pods
## e.g.
## - name: your-image-name
##   image: your-image
##   imagePullPolicy: Always
##   ports:
##     - name: portname
##        containerPort: 1234
##
sidecars: []
## Add an horizontal pod autoscaler
## ref: https://kubernetes.io/docs/tasks/run-application/horizontal-pod-autoscale/
## @param autoscaling.enabled Deploy a HorizontalPodAutoscaler object for the Kong deployment
## @param autoscaling.minReplicas Minimum number of replicas to scale back
## @param autoscaling.maxReplicas Maximum number of replicas to scale out
## @param autoscaling.metrics [array] Metrics to use when deciding to scale the deployment (evaluated as a template)
##
autoscaling:
  enabled: false
  minReplicas: 2
  maxReplicas: 5
  metrics:
    - type: Resource
      resource:
        name: cpu
        targetAverageUtilization: 80
## Kong Pod Disruption Budget
## ref: https://kubernetes.io/docs/concepts/workloads/pods/disruptions/
## @param pdb.create Deploy a PodDisruptionBudget object for Kong deployment
## @param pdb.minAvailable Minimum available Kong replicas (expressed in percentage)
## @param pdb.maxUnavailable Maximum unavailable Kong replicas (expressed in percentage)
##
pdb:
  create: false
  minAvailable: ""
  maxUnavailable: "50%"
## @section Kong Container Parameters
##
kong:
  ## @param kong.command Override default container command (useful when using custom images)
  ##
  command: []
  ## @param kong.args Override default container args (useful when using custom images)
  ##
  args: []
  ## @param kong.initScriptsCM Configmap with init scripts to execute
  ## ConfigMap containing `/docker-entrypoint-initdb.d` scripts to be executed at initialization time (evaluated as a template)
  ##
  initScriptsCM: ""
  ## @param kong.initScriptsSecret Configmap with init scripts to execute
  ## Secret containing `/docker-entrypoint-initdb.d` scripts to be executed at initialization time (that contain sensitive data). Evaluated as a template.
  ##
  initScriptsSecret: ""
  ## @param kong.declarativeConfig Declarative configuration to be loaded by Kong (evaluated as a template)
  ## https://docs.konghq.com/gateway/latest/production/deployment-topologies/db-less-and-declarative-config/
  ##
  declarativeConfig: ""
  ## @param kong.declarativeConfigCM Configmap with declarative configuration to be loaded by Kong (evaluated as a template)
  ## https://docs.konghq.com/gateway/latest/production/deployment-topologies/db-less-and-declarative-config/
  ##
  declarativeConfigCM: ""
  ## @param kong.extraEnvVars Array containing extra env vars to configure Kong
  ## For example:
  ## extraEnvVars:
  ##  - name: GF_DEFAULT_INSTANCE_NAME
  ##    value: my-instance
  ##
  extraEnvVars: []
  ## @param kong.extraEnvVarsCM ConfigMap containing extra env vars to configure Kong
  ##
  extraEnvVarsCM: ""
  ## @param kong.extraEnvVarsSecret Secret containing extra env vars to configure Kong (in case of sensitive data)
  ##
  extraEnvVarsSecret: ""
  ## @param kong.extraVolumeMounts Array of extra volume mounts to be added to the Kong Container (evaluated as template). Normally used with `extraVolumes`.
  ##
  extraVolumeMounts: []
  ## @param kong.containerPorts.proxyHttp Kong proxy HTTP container port
  ## @param kong.containerPorts.proxyHttps Kong proxy HTTPS container port
  ## @param kong.containerPorts.adminHttp Kong admin HTTP container port
  ## @param kong.containerPorts.adminHttps Kong admin HTTPS container port
  ##
  containerPorts:
    proxyHttp: 8000
    proxyHttps: 8443
    adminHttp: 8001
    adminHttps: 8444
  ## Container resource requests and limits
  ## ref: https://kubernetes.io/docs/concepts/configuration/manage-compute-resources-container/
  ## @param kong.resourcesPreset Set container resources according to one common preset (allowed values: none, nano, micro, small, medium, large, xlarge, 2xlarge). This is ignored if kong.resources is set (kong.resources is recommended for production).
  ## More information: https://github.com/bitnami/charts/blob/main/bitnami/common/templates/_resources.tpl#L15
  ##
  resourcesPreset: "medium"
  ## @param kong.resources Set container requests and limits for different resources like CPU or memory (essential for production workloads)
  ## Example:
  ## resources:
  ##   requests:
  ##     cpu: 2
  ##     memory: 512Mi
  ##   limits:
  ##     cpu: 3
  ##     memory: 1024Mi
  ##
  resources: {}
  ## Configure extra options for Kong containers' liveness, readiness and startup probes
  ## ref: https://kubernetes.io/docs/tasks/configure-pod-container/configure-liveness-readiness-startup-probes/#configure-probes
  ## @param kong.livenessProbe.enabled Enable livenessProbe on Kong containers
  ## @param kong.livenessProbe.initialDelaySeconds Initial delay seconds for livenessProbe
  ## @param kong.livenessProbe.periodSeconds Period seconds for livenessProbe
  ## @param kong.livenessProbe.timeoutSeconds Timeout seconds for livenessProbe
  ## @param kong.livenessProbe.failureThreshold Failure threshold for livenessProbe
  ## @param kong.livenessProbe.successThreshold Success threshold for livenessProbe
  ##
  livenessProbe:
    enabled: true
    initialDelaySeconds: 120
    periodSeconds: 10
    timeoutSeconds: 5
    failureThreshold: 6
    successThreshold: 1
  ## @param kong.readinessProbe.enabled Enable readinessProbe on Kong containers
  ## @param kong.readinessProbe.initialDelaySeconds Initial delay seconds for readinessProbe
  ## @param kong.readinessProbe.periodSeconds Period seconds for readinessProbe
  ## @param kong.readinessProbe.timeoutSeconds Timeout seconds for readinessProbe
  ## @param kong.readinessProbe.failureThreshold Failure threshold for readinessProbe
  ## @param kong.readinessProbe.successThreshold Success threshold for readinessProbe
  ##
  readinessProbe:
    enabled: true
    initialDelaySeconds: 30
    periodSeconds: 10
    timeoutSeconds: 5
    failureThreshold: 6
    successThreshold: 1
  ## @param kong.startupProbe.enabled Enable startupProbe on Kong containers
  ## @param kong.startupProbe.initialDelaySeconds Initial delay seconds for startupProbe
  ## @param kong.startupProbe.periodSeconds Period seconds for startupProbe
  ## @param kong.startupProbe.timeoutSeconds Timeout seconds for startupProbe
  ## @param kong.startupProbe.failureThreshold Failure threshold for startupProbe
  ## @param kong.startupProbe.successThreshold Success threshold for startupProbe
  ##
  startupProbe:
    enabled: false
    initialDelaySeconds: 10
    periodSeconds: 15
    timeoutSeconds: 3
    failureThreshold: 20
    successThreshold: 1
  ## @param kong.customLivenessProbe Override default liveness probe (kong container)
  ##
  customLivenessProbe: {}
  ## @param kong.customReadinessProbe Override default readiness probe (kong container)
  ##
  customReadinessProbe: {}
  ## @param kong.customStartupProbe Override default startup probe (kong container)
  ##
  customStartupProbe: {}
  ## @param kong.lifecycleHooks Lifecycle hooks (kong container)
  ## ref: https://kubernetes.io/docs/concepts/containers/container-lifecycle-hooks/
  ##
  lifecycleHooks: {}
## @section Traffic Exposure Parameters
##

## Service parameters
##
service:
  ## @param service.type Kubernetes Service type
  ##
  type: ClusterIP
  ## @param service.exposeAdmin Add the Kong Admin ports to the service
  ##
  exposeAdmin: false
  ## @param service.disableHttpPort Disable Kong proxy HTTP and Kong admin HTTP ports
  ##
  disableHttpPort: false
  ## @param service.ports.proxyHttp Kong proxy service HTTP port
  ## @param service.ports.proxyHttps Kong proxy service HTTPS port
  ## @param service.ports.adminHttp Kong admin service HTTP port (only if service.exposeAdmin=true)
  ## @param service.ports.adminHttps Kong admin service HTTPS port (only if service.exposeAdmin=true)
  ##
  ports:
    proxyHttp: 80
    proxyHttps: 443
    adminHttp: 8001
    adminHttps: 8444
  ## @param service.nodePorts.proxyHttp NodePort for the Kong proxy HTTP endpoint
  ## @param service.nodePorts.proxyHttps NodePort for the Kong proxy HTTPS endpoint
  ## @param service.nodePorts.adminHttp NodePort for the Kong admin HTTP endpoint
  ## @param service.nodePorts.adminHttps NodePort for the Kong admin HTTPS endpoint
  ## NOTE: choose port between <30000-32767>
  ##
  nodePorts:
    proxyHttp: ""
    proxyHttps: ""
    adminHttp: ""
    adminHttps: ""
  ## @param service.sessionAffinity Control where client requests go, to the same pod or round-robin
  ## Values: ClientIP or None
  ## ref: https://kubernetes.io/docs/concepts/services-networking/service/
  ##
  sessionAffinity: None
  ## @param service.sessionAffinityConfig Additional settings for the sessionAffinity
  ## sessionAffinityConfig:
  ##   clientIP:
  ##     timeoutSeconds: 300
  ##
  sessionAffinityConfig: {}
  ## @param service.clusterIP Cluster internal IP of the service
  ## This is the internal IP address of the service and is usually assigned randomly.
  ## ref: https://kubernetes.io/docs/reference/kubernetes-api/service-resources/service-v1/#ServiceSpec
  ##
  clusterIP: ""
  ## @param service.externalTrafficPolicy external traffic policy managing client source IP preservation
  ## default to "Cluster"
  ## set to "Local" in order to preserve the client source IP (only on service of type LoadBalancer or NodePort)
  ## ref: https://kubernetes.io/docs/tasks/access-application-cluster/create-external-load-balancer/
  ##
  externalTrafficPolicy: ""
  ## @param service.loadBalancerIP loadBalancerIP if kong service type is `LoadBalancer`
  ## ref: https://kubernetes.io/docs/concepts/services-networking/service/#type-loadbalancer
  ##
  loadBalancerIP: ""
  ## @param service.loadBalancerSourceRanges Kong service Load Balancer sources
  ## ref: https://kubernetes.io/docs/tasks/access-application-cluster/configure-cloud-provider-firewall/#restrict-access-for-loadbalancer-service
  ## e.g:
  ## loadBalancerSourceRanges:
  ##   - 10.10.10.0/24
  ##
  loadBalancerSourceRanges: []
  ## @param service.annotations Annotations for Kong service
  ## set the LoadBalancer service type to internal only.
  ## ref: https://kubernetes.io/docs/concepts/services-networking/service/#internal-load-balancer
  ##
  annotations: {}
  ## @param service.extraPorts Extra ports to expose (normally used with the `sidecar` value)
  ##
  extraPorts: []
## Network Policies
## Ref: https://kubernetes.io/docs/concepts/services-networking/network-policies/
##
networkPolicy:
  ## @param networkPolicy.enabled Specifies whether a NetworkPolicy should be created
  ##
  enabled: true
  ## @param networkPolicy.allowExternal Don't require server label for connections
  ## The Policy model to apply. When set to false, only pods with the correct
  ## server label will have network access to the ports server is listening
  ## on. When true, server will accept connections from any source
  ## (with the correct destination port).
  ##
  allowExternal: true
  ## @param networkPolicy.allowExternalEgress Allow the pod to access any range of port and all destinations.
  ##
  allowExternalEgress: true
  ## @param networkPolicy.kubeAPIServerPorts [array] List of possible endpoints to kube-apiserver (limit to your cluster settings to increase security)
  ##
  kubeAPIServerPorts: [443, 6443, 8443]
  ## @param networkPolicy.extraIngress [array] Add extra ingress rules to the NetworkPolicy
  ## e.g:
  ## extraIngress:
  ##   - ports:
  ##       - port: 1234
  ##     from:
  ##       - podSelector:
  ##           - matchLabels:
  ##               - role: frontend
  ##       - podSelector:
  ##           - matchExpressions:
  ##               - key: role
  ##                 operator: In
  ##                 values:
  ##                   - frontend
  extraIngress: []
  ## @param networkPolicy.extraEgress [array] Add extra ingress rules to the NetworkPolicy
  ## e.g:
  ## extraEgress:
  ##   - ports:
  ##       - port: 1234
  ##     to:
  ##       - podSelector:
  ##           - matchLabels:
  ##               - role: frontend
  ##       - podSelector:
  ##           - matchExpressions:
  ##               - key: role
  ##                 operator: In
  ##                 values:
  ##                   - frontend
  ##
  extraEgress: []
  ## @param networkPolicy.ingressNSMatchLabels [object] Labels to match to allow traffic from other namespaces
  ## @param networkPolicy.ingressNSPodMatchLabels [object] Pod labels to match to allow traffic from other namespaces
  ##
  ingressNSMatchLabels: {}
  ingressNSPodMatchLabels: {}
## Configure the ingress resource that allows you to access the
## Kong installation. Set up the URL
## ref: https://kubernetes.io/docs/concepts/services-networking/ingress/
##
ingress:
  ## @param ingress.enabled Enable ingress controller resource
  ##
  enabled: false
  ## @param ingress.ingressClassName IngressClass that will be be used to implement the Ingress (Kubernetes 1.18+)
  ## This is supported in Kubernetes 1.18+ and required if you have more than one IngressClass marked as the default for your cluster.
  ## ref: https://kubernetes.io/blog/2020/04/02/improvements-to-the-ingress-api-in-kubernetes-1.18/
  ##
  ingressClassName: ""
  ## @param ingress.pathType Ingress path type
  ##
  pathType: ImplementationSpecific
  ## @param ingress.apiVersion Force Ingress API version (automatically detected if not set)
  ##
  apiVersion: ""
  ## @param ingress.hostname Default host for the ingress resource
  ##
  hostname: kong.local
  ## @param ingress.path Ingress path
  ## NOTE: You may need to set this to '/*' in order to use this with ALB ingress controllers
  ##
  path: /
  ## @param ingress.annotations [object] Additional annotations for the Ingress resource. To enable certificate autogeneration, place here your cert-manager annotations.
  ## Use this parameter to set the required annotations for cert-manager, see
  ## ref: https://cert-manager.io/docs/usage/ingress/#supported-annotations
  ## e.g:
  ## annotations:
  ##   kubernetes.io/ingress.class: nginx
  ##   cert-manager.io/cluster-issuer: cluster-issuer-name
  ##
  annotations: {}
  ## @param ingress.tls Enable TLS configuration for the host defined at `ingress.hostname` parameter
  ## TLS certificates will be retrieved from a TLS secret with name: `{{- printf "%s-tls" .Values.ingress.hostname }}`
  ## You can:
  ##   - Use the `ingress.secrets` parameter to create this TLS secret
  ##   - Rely on cert-manager to create it by setting the corresponding annotations
  ##   - Rely on Helm to create self-signed certificates by setting `ingress.selfSigned=true`
  ##
  tls: false
  ## @param ingress.selfSigned Create a TLS secret for this ingress record using self-signed certificates generated by Helm
  ##
  selfSigned: false
  ## @param ingress.extraHosts The list of additional hostnames to be covered with this ingress record.
  ## Most likely the hostname above will be enough, but in the event more hosts are needed, this is an array
  ## extraHosts:
  ## - name: kong.local
  ##   path: /
  ##
  extraHosts: []
  ## @param ingress.extraPaths Additional arbitrary path/backend objects
  ## For example: The ALB ingress controller requires a special rule for handling SSL redirection.
  ## extraPaths:
  ## - path: /*
  ##   backend:
  ##     serviceName: ssl-redirect
  ##     servicePort: use-annotation
  ##
  extraPaths: []
  ## @param ingress.extraTls The tls configuration for additional hostnames to be covered with this ingress record.
  ## see: https://kubernetes.io/docs/concepts/services-networking/ingress/#tls
  ## extraTls:
  ## - hosts:
  ##     - kong.local
  ##   secretName: kong.local-tls
  ##
  extraTls: []
  ## @param ingress.secrets If you're providing your own certificates, please use this to add the certificates as secrets
  ## key and certificate should start with -----BEGIN CERTIFICATE----- or
  ## -----BEGIN RSA PRIVATE KEY-----
  ##
  ## name should line up with a tlsSecret set further up
  ## If you're using cert-manager, this is unneeded, as it will create the secret for you if it is not set
  ##
  ## It is also possible to create and manage the certificates outside of this helm chart
  ## Please see README.md for more information
  ## e.g:
  ## secrets:
  ## - name: kong.local-tls
  ##   key:
  ##   certificate:
  ##
  ##
  secrets: []
  ## @param ingress.extraRules Additional rules to be covered with this ingress record
  ## ref: https://kubernetes.io/docs/concepts/services-networking/ingress/#ingress-rules
  ## e.g:
  ## extraRules:
  ## - host: example.local
  ##     http:
  ##       path: /
  ##       backend:
  ##         service:
  ##           name: example-svc
  ##           port:
  ##             name: http
  ##
  extraRules: []
## @section Kong Ingress Controller Container Parameters
##
ingressController:
  ## @param ingressController.enabled Enable/disable the Kong Ingress Controller
  ##
  enabled: true
  ## @param ingressController.image.registry [default: REGISTRY_NAME] Kong Ingress Controller image registry
  ## @param ingressController.image.repository [default: REPOSITORY_NAME/kong-ingress-controller] Kong Ingress Controller image name
  ## @skip ingressController.image.tag Kong Ingress Controller image tag
  ## @param ingressController.image.digest Kong Ingress Controller image digest in the way sha256:aa.... Please note this parameter, if set, will override the tag
  ## @param ingressController.image.pullPolicy Kong Ingress Controller image pull policy
  ## @param ingressController.image.pullSecrets Specify docker-registry secret names as an array
  ##
  image:
    registry: docker.io
    repository: bitnami/kong-ingress-controller
    tag: 3.1.5-debian-12-r0
    digest: ""
    ## Specify a imagePullPolicy
    ## Defaults to 'Always' if image tag is 'latest', else set to 'IfNotPresent'
    ## ref: https://kubernetes.io/docs/concepts/containers/images/#pre-pulled-images
    ##
    pullPolicy: IfNotPresent
    ## Optionally specify an array of imagePullSecrets.
    ## Secrets must be manually created in the namespace.
    ## ref: https://kubernetes.io/docs/tasks/configure-pod-container/pull-image-private-registry/
    ## E.g:
    ## pullSecrets:
    ##   - myRegistryKeySecretName
    ##
    pullSecrets: []
  ## @param ingressController.proxyReadyTimeout Maximum time (in seconds) to wait for the Kong container to be ready
  ##
  proxyReadyTimeout: 300
  ## @param ingressController.ingressClass Name of the class to register Kong Ingress Controller (useful when having other Ingress Controllers in the cluster)
  ##
  ingressClass: kong
  ## @param ingressController.command Override default container command (useful when using custom images)
  ##
  command: []
  ## @param ingressController.args Override default container args (useful when using custom images)
  ##
  args: []
  ## @param ingressController.extraEnvVars Array containing extra env vars to configure Kong
  ## For example:
  ## extraEnvVars:
  ##  - name: GF_DEFAULT_INSTANCE_NAME
  ##    value: my-instance
  ##
  extraEnvVars: []
  ## @param ingressController.extraEnvVarsCM ConfigMap containing extra env vars to configure Kong Ingress Controller
  ##
  extraEnvVarsCM: ""
  ## @param ingressController.extraEnvVarsSecret Secret containing extra env vars to configure Kong Ingress Controller (in case of sensitive data)
  ##
  extraEnvVarsSecret: ""
  ## @param ingressController.extraVolumeMounts Array of extra volume mounts to be added to the Kong Ingress Controller container (evaluated as template). Normally used with `extraVolumes`.
  ##
  extraVolumeMounts: []
  ## @param ingressController.containerPorts.health Kong Ingress Controller health container port
  ##
  containerPorts:
    health: 10254
  ## Container resource requests and limits
  ## ref: https://kubernetes.io/docs/concepts/configuration/manage-compute-resources-container/
  ## @param ingressController.resourcesPreset Set container resources according to one common preset (allowed values: none, nano, micro, small, medium, large, xlarge, 2xlarge). This is ignored if ingressController.resources is set (ingressController.resources is recommended for production).
  ## More information: https://github.com/bitnami/charts/blob/main/bitnami/common/templates/_resources.tpl#L15
  ##
  resourcesPreset: "nano"
  ## @param ingressController.resources Set container requests and limits for different resources like CPU or memory (essential for production workloads)
  ## Example:
  ## resources:
  ##   requests:
  ##     cpu: 2
  ##     memory: 512Mi
  ##   limits:
  ##     cpu: 3
  ##     memory: 1024Mi
  ##
  resources: {}
  ## Configure extra options for Kong Ingress Controller containers' liveness, readiness and startup probes
  ## ref: https://kubernetes.io/docs/tasks/configure-pod-container/configure-liveness-readiness-startup-probes/#configure-probes
  ## @param ingressController.livenessProbe.enabled Enable livenessProbe on Kong Ingress Controller containers
  ## @param ingressController.livenessProbe.initialDelaySeconds Initial delay seconds for livenessProbe
  ## @param ingressController.livenessProbe.periodSeconds Period seconds for livenessProbe
  ## @param ingressController.livenessProbe.timeoutSeconds Timeout seconds for livenessProbe
  ## @param ingressController.livenessProbe.failureThreshold Failure threshold for livenessProbe
  ## @param ingressController.livenessProbe.successThreshold Success threshold for livenessProbe
  ##
  livenessProbe:
    enabled: true
    initialDelaySeconds: 120
    periodSeconds: 10
    timeoutSeconds: 5
    failureThreshold: 6
    successThreshold: 1
  ## @param ingressController.readinessProbe.enabled Enable readinessProbe on Kong Ingress Controller containers
  ## @param ingressController.readinessProbe.initialDelaySeconds Initial delay seconds for readinessProbe
  ## @param ingressController.readinessProbe.periodSeconds Period seconds for readinessProbe
  ## @param ingressController.readinessProbe.timeoutSeconds Timeout seconds for readinessProbe
  ## @param ingressController.readinessProbe.failureThreshold Failure threshold for readinessProbe
  ## @param ingressController.readinessProbe.successThreshold Success threshold for readinessProbe
  ##
  readinessProbe:
    enabled: true
    initialDelaySeconds: 30
    periodSeconds: 10
    timeoutSeconds: 5
    failureThreshold: 6
    successThreshold: 1
  ## @param ingressController.startupProbe.enabled Enable startupProbe on Kong Ingress Controller containers
  ## @param ingressController.startupProbe.initialDelaySeconds Initial delay seconds for startupProbe
  ## @param ingressController.startupProbe.periodSeconds Period seconds for startupProbe
  ## @param ingressController.startupProbe.timeoutSeconds Timeout seconds for startupProbe
  ## @param ingressController.startupProbe.failureThreshold Failure threshold for startupProbe
  ## @param ingressController.startupProbe.successThreshold Success threshold for startupProbe
  ##
  startupProbe:
    enabled: false
    initialDelaySeconds: 10
    periodSeconds: 15
    timeoutSeconds: 3
    failureThreshold: 20
    successThreshold: 1
  ## @param ingressController.customLivenessProbe Override default liveness probe (Kong Ingress Controller container)
  ##
  customLivenessProbe: {}
  ## @param ingressController.customReadinessProbe Override default readiness probe (Kong Ingress Controller container)
  ##
  customReadinessProbe: {}
  ## @param ingressController.customStartupProbe Override default startup probe (Kong Ingress Controller container)
  ##
  customStartupProbe: {}
  ## @param ingressController.lifecycleHooks Lifecycle hooks (Kong Ingress Controller container)
  ## ref: https://kubernetes.io/docs/concepts/containers/container-lifecycle-hooks/
  ##
  lifecycleHooks: {}
  ## @param ingressController.serviceAccount.create Enable the creation of a ServiceAccount for Kong pods
  ## @param ingressController.serviceAccount.name Name of the created ServiceAccount (name generated using common.names.fullname template otherwise)
  ## @param ingressController.serviceAccount.automountServiceAccountToken Auto-mount the service account token in the pod
  ## @param ingressController.serviceAccount.annotations Additional custom annotations for the ServiceAccount
  ##
  serviceAccount:
    create: true
    name: ""
    automountServiceAccountToken: false
    annotations: {}
  ## @param ingressController.rbac.create Create the necessary RBAC resources for the Ingress Controller to work
  ## @param ingressController.rbac.rules Custom RBAC rules
  ##
  rbac:
    create: true
    ## Example:
    ## rules:
    ##   - apiGroups:
    ##       - ""
    ##     resources:
    ##       - pods
    ##     verbs:
    ##       - get
    ##       - list
    ##
    rules: []
## @section Kong Migration job Parameters
##
migration:
  ## In case you want to use a custom image for Kong migration, set this value
  ## image:
  ##   registry:
  ##   repository:
  ##   tag:
  ##   digest: ""
  ##
  ## @param migration.command Override default container command (useful when using custom images)
  ##
  command: []
  ## @param migration.args Override default container args (useful when using custom images)
  ##
  args: []
  ## @param migration.extraEnvVars Array containing extra env vars to configure the Kong migration job
  ## For example:
  ## extraEnvVars:
  ##  - name: GF_DEFAULT_INSTANCE_NAME
  ##    value: my-instance
  ##
  extraEnvVars: []
  ## @param migration.extraEnvVarsCM ConfigMap containing extra env vars to configure the Kong migration job
  ##
  extraEnvVarsCM: ""
  ## @param migration.extraEnvVarsSecret Secret containing extra env vars to configure the Kong migration job (in case of sensitive data)
  ##
  extraEnvVarsSecret: ""
  ## @param migration.extraVolumeMounts Array of extra volume mounts to be added to the Kong Container (evaluated as template). Normally used with `extraVolumes`.
  ##
  extraVolumeMounts: []
  ## Container resource requests and limits
  ## ref: https://kubernetes.io/docs/concepts/configuration/manage-compute-resources-container/
  ## @param migration.resourcesPreset Set container resources according to one common preset (allowed values: none, nano, micro, small, medium, large, xlarge, 2xlarge). This is ignored if migration.resources is set (migration.resources is recommended for production).
  ## More information: https://github.com/bitnami/charts/blob/main/bitnami/common/templates/_resources.tpl#L15
  ##
  resourcesPreset: "nano"
  ## @param migration.resources Set container requests and limits for different resources like CPU or memory (essential for production workloads)
  ## Example:
  ## resources:
  ##   requests:
  ##     cpu: 2
  ##     memory: 512Mi
  ##   limits:
  ##     cpu: 3
  ##     memory: 1024Mi
  ##
  resources: {}
  ## @param migration.automountServiceAccountToken Mount Service Account token in pod
  ##
  automountServiceAccountToken: true
  ## @param migration.hostAliases Add deployment host aliases
  ## https://kubernetes.io/docs/concepts/services-networking/add-entries-to-pod-etc-hosts-with-host-aliases/
  ##
  hostAliases: []
  ## @param migration.annotations [object] Add annotations to the job
  ##
  annotations:
    helm.sh/hook: post-install, pre-upgrade
    helm.sh/hook-delete-policy: before-hook-creation,hook-succeeded
  ## @param migration.podLabels Additional pod labels
  ## Ref: https://kubernetes.io/docs/concepts/overview/working-with-objects/labels/
  ##
  podLabels: {}
  ## @param migration.podAnnotations Additional pod annotations
  ## ref: https://kubernetes.io/docs/concepts/overview/working-with-objects/annotations/
  ##
  podAnnotations: {}
## @section PostgreSQL Parameters
##

## PostgreSQL chart configuration
## ref: https://github.com/bitnami/charts/blob/main/bitnami/postgresql/values.yaml
## @param postgresql.enabled Switch to enable or disable the PostgreSQL helm chart
## @param postgresql.auth.postgresPassword Password for the "postgres" admin user
## @param postgresql.auth.username Name for a custom user to create
## @param postgresql.auth.password Password for the custom user to create
## @param postgresql.auth.database Name for a custom database to create
## @param postgresql.auth.existingSecret Name of existing secret to use for PostgreSQL credentials
## @param postgresql.auth.usePasswordFiles Mount credentials as a files instead of using an environment variable
## @param postgresql.architecture PostgreSQL architecture (`standalone` or `replication`)
##
postgresql:
  enabled: true
  ## Override PostgreSQL default image as 14.x is not supported
  ## ref: https://github.com/bitnami/containers/tree/main/bitnami/postgresql
  ## @param postgresql.image.registry [default: REGISTRY_NAME] PostgreSQL image registry
  ## @param postgresql.image.repository [default: REPOSITORY_NAME/postgresql] PostgreSQL image repository
  ## @skip postgresql.image.tag PostgreSQL image tag (immutable tags are recommended)
  ## @param postgresql.image.digest PostgreSQL image digest in the way sha256:aa.... Please note this parameter, if set, will override the tag
  ##
  image:
    registry: docker.io
    repository: bitnami/postgresql
    tag: 14.12.0-debian-12-r4
    digest: ""
  auth:
    username: kong
    password: ""
    database: kong
    postgresPassword: ""
    existingSecret: ""
    usePasswordFiles: false
  architecture: standalone
  primary:
    ## PostgreSQL Primary resource requests and limits
    ## ref: https://kubernetes.io/docs/concepts/configuration/manage-compute-resources-container/
    ## @param postgresql.primary.resourcesPreset Set container resources according to one common preset (allowed values: none, nano, small, medium, large, xlarge, 2xlarge). This is ignored if primary.resources is set (primary.resources is recommended for production).
    ## More information: https://github.com/bitnami/charts/blob/main/bitnami/common/templates/_resources.tpl#L15
    ##
    resourcesPreset: "nano"
    ## @param postgresql.primary.resources Set container requests and limits for different resources like CPU or memory (essential for production workloads)
    ## Example:
    ## resources:
    ##   requests:
    ##     cpu: 2
    ##     memory: 512Mi
    ##   limits:
    ##     cpu: 3
    ##     memory: 1024Mi
    ##
    resources: {}
  ## External PostgreSQL configuration
  ## All of these values are only used when postgresql.enabled is set to false
  ## @param postgresql.external.host Database host
  ## @param postgresql.external.port Database port number
  ## @param postgresql.external.user Non-root username for Kong
  ## @param postgresql.external.password Password for the non-root username for Kong
  ## @param postgresql.external.database Kong database name
  ## @param postgresql.external.existingSecret Name of an existing secret resource containing the database credentials
  ## @param postgresql.external.existingSecretPasswordKey Name of an existing secret key containing the database credentials
  ##
  external:
    host: ""
    port: 5432
    user: kong
    password: ""
    database: kong
    existingSecret: ""
    existingSecretPasswordKey: ""
## @section Cassandra Parameters
##

## Cassandra chart configuration
## ref: https://github.com/bitnami/charts/blob/main/bitnami/cassandra/values.yaml
## @param cassandra.enabled Switch to enable or disable the Cassandra helm chart
## @param cassandra.dbUser.user Cassandra admin user
## @param cassandra.dbUser.password Password for `cassandra.dbUser.user`. Randomly generated if empty
## @param cassandra.dbUser.existingSecret Name of existing secret to use for Cassandra credentials
## @param cassandra.usePasswordFile Mount credentials as a files instead of using an environment variable
## @param cassandra.replicaCount Number of Cassandra replicas
##
cassandra:
  enabled: false
  dbUser:
    user: kong
    password: ""
    existingSecret: ""
  usePasswordFile: false
  replicaCount: 1
  ## External Cassandra configuration
  ## All of these values are only used when cassandra.enabled is set to false
  ## @param cassandra.external.hosts List of Cassandra hosts
  ## @param cassandra.external.port Cassandra port number
  ## @param cassandra.external.user Username of the external cassandra installation
  ## @param cassandra.external.password Password of the external cassandra installation
  ## @param cassandra.external.existingSecret Name of an existing secret resource containing the Cassandra credentials
  ## @param cassandra.external.existingSecretPasswordKey Name of an existing secret key containing the Cassandra credentials
  ##
  external:
    hosts: []
    port: 9042
    user: ""
    password: ""
    existingSecret: ""
    existingSecretPasswordKey: ""
  ## Cassandra pods' resource requests and limits
  ## ref: https://kubernetes.io/docs/concepts/configuration/manage-compute-resources-container/
  ## Minimum memory for development is 4GB and 2 CPU cores
  ## Minimum memory for production is 8GB and 4 CPU cores
  ## ref: http://docs.datastax.com/en/archived/cassandra/2.0/cassandra/architecture/architecturePlanningHardware_c.html
  ##
  ## We usually recommend not to specify default resources and to leave this as a conscious
  ## choice for the user. This also increases chances charts run on environments with little
  ## resources, such as Minikube. If you do want to specify resources, uncomment the following
  ## lines, adjust them as necessary, and remove the curly braces after 'resources:'.
  ## @param cassandra.resourcesPreset Set container resources according to one common preset (allowed values: none, nano, small, medium, large, xlarge, 2xlarge). This is ignored if resources is set (resources is recommended for production).
  ## More information: https://github.com/bitnami/charts/blob/main/bitnami/common/templates/_resources.tpl#L15
  ##
  resourcesPreset: "large"
  ## @param cassandra.resources Set container requests and limits for different resources like CPU or memory (essential for production workloads)
  ## Example:
  ## resources:
  ##   requests:
  ##     cpu: 2
  ##     memory: 512Mi
  ##   limits:
  ##     cpu: 3
  ##     memory: 1024Mi
  ##
  resources: {}
## @section Metrics Parameters
##

## Prometheus metrics
##
metrics:
  ## @param metrics.enabled Enable the export of Prometheus metrics
  ##
  enabled: false
  ## @param metrics.containerPorts.http Prometheus metrics HTTP container port
  ##
  containerPorts:
    http: 9119
  ## Kong metrics service configuration
  ##
  service:
    ## @param metrics.service.sessionAffinity Control where client requests go, to the same pod or round-robin
    ## Values: ClientIP or None
    ## ref: https://kubernetes.io/docs/concepts/services-networking/service/
    ##
    sessionAffinity: None
    ## @param metrics.service.clusterIP Cluster internal IP of the service
    ## This is the internal IP address of the service and is usually assigned randomly.
    ## ref: https://kubernetes.io/docs/reference/kubernetes-api/service-resources/service-v1/#ServiceSpec
    ##
    clusterIP: ""
    ## @param metrics.service.annotations [object] Annotations for Prometheus metrics service
    ##
    annotations:
      prometheus.io/scrape: "true"
      prometheus.io/port: "{{ coalesce .Values.metrics.service.ports.http .Values.metrics.service.port }}"
      prometheus.io/path: "/metrics"
    ## @param metrics.service.ports.http Prometheus metrics service HTTP port
    ##
    ports:
      http: 9119
  ## Kong ServiceMonitor configuration
  ##
  serviceMonitor:
    ## @param metrics.serviceMonitor.enabled Create ServiceMonitor Resource for scraping metrics using PrometheusOperator
    ##
    enabled: false
    ## @param metrics.serviceMonitor.namespace Namespace which Prometheus is running in
    ##
    namespace: ""
    ## @param metrics.serviceMonitor.interval Interval at which metrics should be scraped
    ##
    interval: 30s
    ## @param metrics.serviceMonitor.scrapeTimeout Specify the timeout after which the scrape is ended
    ## e.g:
    ##   scrapeTimeout: 30s
    ##
    scrapeTimeout: ""
    ## @param metrics.serviceMonitor.labels Additional labels that can be used so ServiceMonitor will be discovered by Prometheus
    ##
    labels: {}
    ## @param metrics.serviceMonitor.selector Prometheus instance selector labels
    ## ref: https://github.com/bitnami/charts/tree/main/bitnami/prometheus-operator#prometheus-configuration
    ##
    selector: {}
    ## @param metrics.serviceMonitor.relabelings RelabelConfigs to apply to samples before scraping
    ##
    relabelings: []
    ## @param metrics.serviceMonitor.metricRelabelings MetricRelabelConfigs to apply to samples before ingestion
    ##
    metricRelabelings: []
    ## @param metrics.serviceMonitor.honorLabels honorLabels chooses the metric's labels on collisions with target labels
    ##
    honorLabels: false
    ## @param metrics.serviceMonitor.jobLabel The name of the label on the target service to use as the job name in prometheus.
    ##
    jobLabel: ""
    ## @param metrics.serviceMonitor.serviceAccount Service account used by Prometheus Operator
    ##
    serviceAccount: ""
    ## @param metrics.serviceMonitor.rbac.create Create the necessary RBAC resources so Prometheus Operator can reach Kong's namespace
    ##
    rbac:
      create: true
EOF
  ]
}
