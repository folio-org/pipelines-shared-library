import org.folio.Constants
import org.folio.models.EurekaNamespace
import org.folio.models.RancherNamespace
import org.folio.models.module.FolioModule
import org.folio.utilities.Logger

import java.time.LocalDateTime

void withK8sClient(Closure closure) {
  withCredentials([[$class           : 'AmazonWebServicesCredentialsBinding',
                    credentialsId    : Constants.AWS_CREDENTIALS_ID,
                    accessKeyVariable: 'AWS_ACCESS_KEY_ID',
                    secretKeyVariable: 'AWS_SECRET_ACCESS_KEY']]) {
    docker.image(Constants.DOCKER_K8S_CLIENT_IMAGE).inside("-u 0:0 --entrypoint=") {
      closure()
    }
  }
}

void withKubeConfig(String clusterName, Closure closure) {
  withK8sClient {
    awscli.getKubeConfig(Constants.AWS_REGION, clusterName)
    addHelmRepository(Constants.FOLIO_HELM_V2_REPO_NAME, Constants.FOLIO_HELM_V2_REPO_URL, true)
    closure.call()
  }
}

void addHelmRepository(String repo_name, String repo_url, boolean folio_nexus = false) {
  if (folio_nexus) {
    withCredentials([usernamePassword(credentialsId: Constants.NEXUS_PUBLISH_CREDENTIALS_ID, usernameVariable: 'NEXUS_USERNAME', passwordVariable: 'NEXUS_PASSWORD')]) {
      sh "helm repo add ${repo_name} ${repo_url} --username ${NEXUS_USERNAME} --password ${NEXUS_PASSWORD}"
    }
  } else {
    sh "helm repo add ${repo_name} ${repo_url}"
  }
}

void install(String release_name, String namespace, String values_path, String chart_repo, String chart_name) {
  sh "helm install ${release_name} --namespace=${namespace} ${valuesPathOption(values_path)} ${chart_repo}/${chart_name}"
}

void upgrade(String release_name, String namespace, String values_path, String chart_repo, String chart_name) {
  if (release_name.startsWith("mgr-")) {
    sh "helm upgrade --install ${release_name} --namespace=${namespace} ${valuesPathOption(values_path)} ${chart_repo}/${chart_name} --wait"
  } else {
    sh "helm upgrade --install ${release_name} --namespace=${namespace} ${valuesPathOption(values_path)} ${chart_repo}/${chart_name}"
  }
}

void deployFolioModule(RancherNamespace ns, String moduleName, String moduleVersion, boolean customModule = false, String tenantId = ns.defaultTenantId) {
  String valuesFilePath = ""
  String releaseName = moduleName
  String chartName = moduleName
  switch (moduleName) {
    case "okapi":
      valuesFilePath = generateModuleValues(ns, moduleName, moduleVersion, ns.domains['okapi'], customModule)
      break
    case ~/mod-.*/:
      valuesFilePath = generateModuleValues(ns, moduleName, moduleVersion, "", customModule)
      break
    case ~/mgr-.*/:
      valuesFilePath = generateModuleValues(ns, moduleName, moduleVersion, "", customModule)
      break
    case ~/edge-.*/:
      valuesFilePath = generateModuleValues(ns, moduleName, moduleVersion, ns.domains["edge"], customModule)
      break
    case ~/ui-bundle/:
      valuesFilePath = generateModuleValues(ns, moduleName, moduleVersion, ns.tenants[tenantId].tenantUi.domain, false, tenantId)
      releaseName = "${tenantId}-${moduleName}"
      chartName = "platform-complete"
      break
    default:
      new Logger(this, "folioHelm").warning("${moduleName} is not a folio or known module")
      break
  }
  upgrade(releaseName, ns.namespaceName, valuesFilePath, Constants.FOLIO_HELM_V2_REPO_NAME, chartName)
}

void deployFolioModules(RancherNamespace ns, List<FolioModule> modules, boolean customModule = false, String tenantId = ns.defaultTenantId) {
  modules.each { module -> deployFolioModule(ns, module.name, module.version, customModule, tenantId) }
}

void deployFolioModulesParallel(RancherNamespace ns, List<FolioModule> modules, boolean customModule = false, String tenantId = ns.defaultTenantId) {
  int limit = 10
  modules.collate(limit).each { moduleGroup ->
    def branches = [:]
    moduleGroup.each { module ->
      branches[module.name] = {
//        String deployedModuleId = kubectl.getDeploymentImageTag(moduleName, ns.getNamespaceName())
//        if (deployedModuleId != "${moduleName}:${moduleVersion}") {
        deployFolioModule(ns, module.name, module.version, customModule, tenantId)
//        }
      }
    }
    parallel branches
  }
}

void deleteFolioModulesParallel(String ns) {
  def releases = sh(script: "helm list --short --namespace $ns", returnStdout: true).trim()
  int limit = 10
  releases.tokenize().collate(limit).each { release ->
    def branches = [:]
    release.each { rel ->
      branches[rel.toString()] = {
        sh(script: "helm uninstall $rel --namespace $ns")
      }
    }
    parallel branches
  }
}

@Deprecated
/**
 * Use checkDeploymentsRunning functions instead
 */
void checkPodRunning(String ns, String podName) {
  timeout(time: ns == 'ecs-snapshot' ? 15 : 5, unit: 'MINUTES') {
    def podNotRunning = true
    while (podNotRunning) {
      sleep(time: 30, unit: 'SECONDS')

      def fullPodName = sh(script: "kubectl get pods -n ${ns} | grep ${podName} | awk '{print \$1}'", returnStdout: true).trim()

      if (!fullPodName) {
        println "Pod with name containing ${podName} not found. Retrying..."
        continue
      }

      def result = sh(script: "kubectl get pod ${fullPodName} -n ${ns} -o=jsonpath='{.status.phase}'", returnStdout: true).trim()

      podNotRunning = (result != 'Running')

      if (podNotRunning) {
        println("Pod ${fullPodName} is not running. Retrying...")
      } else {
        println("Pod ${fullPodName} is running.")
      }
    }
  }
}

@Deprecated
/**
 * Use checkDeploymentsRunning functions instead
 */
void checkAllPodsRunning(String ns) {
  timeout(time: 20, unit: 'MINUTES')  {
    boolean notAllRunning = true
    while (notAllRunning) {
      sleep(time: 30, unit: 'SECONDS')

      def result = sh(script: "kubectl get pods -n ${ns} --no-headers | awk '{print \$3}'", returnStdout: true).trim()

      notAllRunning = result.split('\n').any { status -> status != 'Running' }

      if (notAllRunning) {
        def evictedPodsList
        println('Not all pods are running. Retrying...')
        try {
          evictedPodsList = sh(script: "kubectl delete pod -n ${ns} --field-selector=\"status.phase==Failed\"", returnStdout: true)
        } catch (Error err) {
          new Logger(this, "managePods").warning("Error: " + err.getMessage() + "\nList of evicted pods: ${evictedPodsList}")
        }
      } else {
        println('All pods are running.')
      }
    }
  }
}

void checkDeploymentsRunning(String ns, FolioModule deploymentModule) {
  checkDeploymentsRunning(ns, [deploymentModule])
}

void checkDeploymentsRunning(String ns, List<FolioModule> deploymentsList) {
  println('Starting deployment monitoring...')

  kubectl.deleteEvictedPods(ns)

  boolean allDeploymentsUpdated = false
  int timer = 0
  int maxTime = 30 * 60 // 30 minutes in seconds

  try {
    while (!allDeploymentsUpdated) {
      def jsonOutput
      try {
        // Execute the kubectl command
        jsonOutput = sh(
          script: "kubectl get deployments -n ${ns} -o json",
          returnStdout: true
        ).trim()
      } catch (Exception e) {
        error("Failed to execute kubectl command: ${e.message}")
      }

      def deploymentsJson
      try {
        // Parse the JSON output
        deploymentsJson = readJSON text: jsonOutput
      } catch (Exception e) {
        error("Failed to parse JSON output: ${e.message}")
      }

      // Check if there are any deployments in the namespace
      if (!deploymentsJson.items || deploymentsJson.items.isEmpty()) {
        error("No deployments found in namespace '${ns}'. Please check the namespace or deployment configuration.")
      }

      def unfinishedDeployments = []

      // Check each deployment from the list
      deploymentsList.each { folioModule ->
        def deployment = deploymentsJson.items.find { it.metadata.name == folioModule.name }
        if (deployment) {
          def status = deployment.status
          def specReplicas = deployment.spec.replicas
          if (status.updatedReplicas != specReplicas ||
              status.readyReplicas != specReplicas ||
              status.unavailableReplicas > 0 ||
              status.conditions.any { it.type == "Available" && it.status == "False" }) {
            unfinishedDeployments << folioModule.name
          }
        } else {
          println("Warning: Deployment '${folioModule.name}' not found in namespace '${ns}'")
        }
      }

      if (unfinishedDeployments) {
        println("Unfinished deployments: ${unfinishedDeployments}")
        println("Rechecking in 30 seconds...")
        sleep(time: 30, unit: 'SECONDS')
        unfinishedDeployments.contains('mod-agreements') ? kubectl.cleanUpAgreementsFedLocks(ns) : println("-=No mod-agreements fed locks to clean up=-") //Would say that it's a workaround, but it's not)))
        timer += 30
      } else {
        println("All deployments are successfully updated!")
        allDeploymentsUpdated = true
      }

      // Check the timer
      if (timer >= maxTime) {
        error("Timeout: Some deployments are still not updated after 20 minutes.")
      }
    }
  } catch (Exception e) {
    // Handle general errors
    println("Error occurred during deployment monitoring: ${e.message}")
    throw e // Rethrow the error to mark the Jenkins build as failed
  }
}




static String valuesPathOption(String path) {
  return path.trim() ? "-f ${path}" : ''
}

String generateModuleValues(RancherNamespace ns, String moduleName, String moduleVersion, String domain = "", boolean customModule = false, String filePostfix = '') {
  String valuesFilePath = filePostfix.trim().isEmpty() ? "./values/${moduleName}.yaml" : "./values/${moduleName}-${filePostfix}.yaml"
  Map moduleConfig = ns.deploymentConfig[moduleName] ? ns.deploymentConfig[moduleName] : new Logger(this, 'folioHelm').error("Values for ${moduleName} not found!")
  String repository = determineModulePlacement(moduleName, moduleVersion, customModule)

  moduleConfig << [image         : [repository: "${repository}/${moduleName}",
                                    tag       : moduleVersion],
                   podAnnotations: [creationTimestamp: "\"${LocalDateTime.now().withNano(0).toString()}\""]]

/**
 * Modules feature switcher*/

// TODO Enable JMX metrics once prometheus will work
//    if (Constants.JMX_METRICS_AVAILABLE[moduleName]) {
//        def action = compare.compareVersion(Constants.JMX_METRICS_AVAILABLE[moduleName], moduleVersion)
//        if (action == "upgrade" || action == "equal") {
//            moduleConfig['javaOptions'] += " -javaagent:./jmx_exporter/jmx_prometheus_javaagent-0.17.2.jar=9991:./jmx_exporter/prometheus-jmx-config.yaml"
//        }
//    }

  if (ns instanceof EurekaNamespace) {
    String sidecarRepository = determineModulePlacement(
      "folio-module-sidecar"
      , ns.getModules().getModuleByName('folio-module-sidecar').getVersion()
    )

    moduleConfig << [
      eureka: [
        enabled: true
      ]
    ]

    moduleConfig += (moduleConfig.sidecarContainers ? [] : [sidecarContainers: [eureka: [:]]])
    moduleConfig.sidecarContainers += (moduleConfig.sidecarContainers?.eureka ? [] : [eureka: [:]])

    moduleConfig.sidecarContainers.eureka << [
      image: [
        repository: "${sidecarRepository}/folio-module-sidecar",
        tag       : ns.getModules().getModuleByName('folio-module-sidecar').getVersion()
      ]
    ]

    switch (moduleName) { // let it still be switch in case we need to add an additional module
      case 'mod-consortia-keycloak':
        println('https://folio-org.atlassian.net/browse/RANCHER-2035')
        break
      case ~/mod-.*-keycloak/:
        moduleConfig['extraEnvVars'] += [
          name: 'MOD_USERS_ID',
          value: 'mod-users-' + ns.getModules().getModuleByName('mod-users').getVersion()
        ]

        break
      case 'mod-requests-mediated':
        moduleConfig['extraEnvVars'] += ns.hasSecureTenant ? [
          name: 'SECURE_TENANT_ID',
          value: ns.getSecureTenant().tenantId
        ] : []

        break
      case 'edge-patron':
        moduleConfig['integrations']['okapi'] = [enabled: false]

        moduleConfig['extraEnvVars'] += ns.hasSecureTenant ? [
          name: 'SECURE_TENANT_ID',
          value: ns.getSecureTenant().tenantId
        ] : []

        moduleConfig['extraEnvVars'] += ns.hasSecureTenant ? [
          name: 'SECURE_REQUESTS_FEATURE_ENABLED',
          value: ns.getSecureTenant().hasSecureTenant
        ] : []

        break
      case ~/edge-.*$/:
        moduleConfig['integrations']['okapi'] = [enabled: false]
        break
    }
  }

  //Enable RTR functionality


  //mod-authtoken jwt.signing.key
  if (moduleName == 'mod-authtoken') {
    if (ns.enableRtr) {
      moduleConfig['extraEnvVars'] += [name: 'LEGACY_TOKEN_TENANTS', value: '']
    }
    moduleConfig['extraJavaOpts'] += ["-Djwt.signing.key=${folioTools.generateRandomString(16)}"]
  }

  //Bulk operations bucket configuration
  if (moduleName == 'mod-bulk-operations' && ns.getNamespaceName() == 'sprint') {
    moduleConfig['extraJavaOpts'] += ['-Dspring.servlet.multipart.max-file-size=40MB',
                                      '-Dspring.servlet.multipart.max-request-size=40MB']
  }

  // Enable extra PVC and initContainer for folio-perf with firebird namespace and folio-testing and sprint namespace
  boolean isSuitableNamespaceAndCluster =
    (ns.getClusterName() == 'folio-perf' && ns.getNamespaceName() == 'firebird') ||
      (ns.getClusterName() == 'folio-dev' && ns.getNamespaceName() == 'firebird') ||
      (ns.getClusterName() == 'folio-testing' && ns.getNamespaceName() == 'sprint')

  if (isSuitableNamespaceAndCluster && moduleName == 'mod-data-export') {
    moduleConfig << [initContainer    : [enabled: true],
                     extraVolumes     : [extendedtmp: [enabled: true]],
                     extraVolumeMounts: [extendedtmp: [enabled: true]],
                     volumeClaims     : [extendedtmp: [enabled: true]]]
  }

  //Toleration and NodeSelector
  if ((ns.getClusterName() == 'folio-testing') && (['cicypress', 'cikarate'].contains(ns.getNamespaceName()))) {
    moduleConfig['nodeSelector'] = ["folio.org/qualitygate": ns.getNamespaceName()]
    moduleConfig['tolerations'] = [[key     : "folio.org/qualitygate",
                                    operator: "Equal",
                                    value   : ns.getNamespaceName(),
                                    effect  : "NoSchedule"]]
  }

  // Enable ingress
  boolean enableIngress = moduleConfig.containsKey('ingress') ? moduleConfig['ingress']['enabled'] : false
  if (enableIngress) {
    moduleConfig['ingress']['hosts'][0] += [host: domain]
    moduleConfig['ingress']['annotations'] += ['alb.ingress.kubernetes.io/group.name': "${ns.clusterName}.${ns.namespaceName}"]
    moduleConfig['ingress']['annotations'] += ['alb.ingress.kubernetes.io/target-type': 'ip']
    moduleConfig['ingress']['annotations'] += ['alb.ingress.kubernetes.io/target-group-attributes': 'deregistration_delay.timeout_seconds=30']
  }

  //Enable edge NLB
  String serviceType = moduleConfig.containsKey('service') ? moduleConfig['service']['type'] : ""
  if (serviceType == "LoadBalancer") {
    def edgeNlbDomain = ""
    switch (moduleName) {
      case 'edge-sip2':
        edgeNlbDomain = common.generateDomain(ns.clusterName, ns.namespaceName, 'sip2', Constants.CI_ROOT_DOMAIN)
        moduleConfig << [okapiUrl         : ns.domains["okapi"],
                         sip2TenantsConfig: """{
  "scTenants": [
    {
      "scSubnet": "0.0.0.0/0",
      "tenant": "${ns.defaultTenantId}",
      "errorDetectionEnabled": false,
      "messageDelimiter": "\\r",
      "charset": "ISO-8859-1"
    }
  ]
}"""]
        break
      case 'edge-connexion':
        edgeNlbDomain = common.generateDomain(ns.clusterName, ns.namespaceName, 'connexion', Constants.CI_ROOT_DOMAIN)
        break
      default:
        new Logger(this, 'folioHelm').warning("Missing nlb configuration for module ${moduleName}")
        break
    }
    moduleConfig['service']['annotations'] += ['external-dns.alpha.kubernetes.io/hostname': edgeNlbDomain]
  }

  writeYaml file: valuesFilePath, data: moduleConfig, overwrite: true
  return valuesFilePath
}

static String determineModulePlacement(String moduleName, String moduleVersion, boolean customModule = false){
  String repository = ""

  if (customModule || moduleName == 'ui-bundle') {
    repository = Constants.ECR_FOLIO_REPOSITORY
  } else {
    switch (moduleVersion) {
      case ~/^\d{1,3}\.\d{1,3}\.\d{1,3}$/:
        repository = "folioorg"
        break
      case ~/^\d{1,3}\.\d{1,3}\.\d{1,3}-SNAPSHOT\.\d{1,3}$/:
        repository = "folioci"
        break
      case ~/^\d{1,3}\.\d{1,3}\.\d{1,3}-SNAPSHOT\.[\d\w]{5,}$/:
        repository = Constants.ECR_FOLIO_REPOSITORY
        break
      case ~/^\d{1,3}\.\d{1,3}\.\d{1,3}-SNAPSHOT\$/:
        repository = Constants.ECR_FOLIO_REPOSITORY
        break
      default:
        repository = "folioci"
        break
    }
  }

  return repository
}
