package folioRancher.namespace.update.ops

import hudson.AbortException
import org.folio.Constants
import org.folio.models.module.EurekaModule
import org.folio.utilities.GitHubClient
import org.folio.utilities.Logger
import org.folio.models.EurekaNamespace
import org.folio.rest_v2.eureka.Eureka
import org.jenkinsci.plugins.workflow.libs.Library

@Library('pipelines-shared-library@RANCHER-2057-test') _

/** Job properties and parameters */
properties([
  buildDiscarder(logRotator(numToKeepStr: '30')),
  disableConcurrentBuilds(),
  parameters([
    folioParameters.moduleName(), // MODULE_NAME, Folio Module name
    folioParameters.moduleSource(), // MODULE_SOURCE, Eureka Module source (Github, DockerHub, AWS ECR)
    folioParameters.branchWithRef('MODULE_BRANCH', 'MODULE_NAME,MODULE_SOURCE'), // MODULE_BRANCH, Eureka Module Github branch
    string(name: 'MAVEN_ARGS', defaultValue: '-DskipTests', description: 'Maven build arguments'),
    folioParameters.containerImageTag('CONTAINER_IMAGE_TAG', 'MODULE_NAME, MODULE_SOURCE'), // CONTAINER_IMAGE_TAG, Container image tag
    folioParameters.cluster(),
    folioParameters.namespace(),
    folioParameters.configType(),
    folioParameters.agent(),
    folioParameters.refreshParameters(),
  ])
])

if (params.REFRESH_PARAMETERS) {
  currentBuild.result = 'ABORTED'
  return
}

input message: "Let's wait"


/** Pipeline variables */
Logger logger = new Logger(this, env.JOB_BASE_NAME)

/** Collect Rancher Namespace Configuration */
EurekaNamespace namespace = new EurekaNamespace(params.CLUSTER, params.NAMESPACE)
  .withDeploymentConfigType(params.CONFIG_TYPE) as EurekaNamespace

/** Assign Desired Resource Profile for Environment */
namespace.addDeploymentConfig(folioTools.getPipelineBranch())

/** Assign Default Tenant Id to Namespace instance */
String defaultTenantId = 'diku'
namespace.withDefaultTenant(defaultTenantId)

/** Collect Folio module configuration */
EurekaModule module = new EurekaModule()
module.name = params.MODULE_NAME

/** Collect feature branch and hash */
String featureBranch = params.MODULE_BRANCH
String featureHash = new GitHubClient(this).getBranchInfo(module.getName(), featureBranch).commit.sha.take(7)

/** Init Eureka Application Instance */
Eureka eureka = new Eureka(this, namespace.generateDomain('kong'), namespace.generateDomain('keycloak'), false)

/** Collect Maven arguments */
String mavenArguments = params.MAVEN_ARGS.trim()

/** Common Gradle Options */
String commonGradleOpts = '--quiet --console plain --no-daemon'

/** Updated Applications Info Map<AppName, AppID> */
Map<String, String> updatedAppInfoMap = [:]

/** Pipeline */
ansiColor('xterm') {
  node(params.AGENT) {
    try {
      stage('Ini') {
        buildName "#${params.MODULE_NAME}.${env.BUILD_ID}"
        buildDescription "Env: ${namespace.getWorkspaceName()}\nBranch: ${params.MODULE_BRANCH}\n" + "Config: ${params.CONFIG_TYPE}\n${currentBuild.getBuildCauses()[0].shortDescription}"

        // Run extra init steps for non-Github module sources
        if (!params.MODULE_SOURCE.contains('github')) {
          // Load Module Details and set Module Version
          module.loadModuleDetails("${module.name}-${params.CONTAINER_IMAGE_TAG}")

          // Load Module Descriptor from Folio Descriptor Registry
          String getModuleDescriptorFromRegistry = "curl -s ${org.folio.rest_v2.Constants.OKAPI_REGISTRY}/_/proxy/modules/${module.name}-${module.version}"
          logger.debug("Module Descriptor URL: ${getModuleDescriptorFromRegistry}")
          String moduleDescriptor = sh(label: "Get Module Descriptor from Folio Descriptor Registry", returnStdout: true, script: getModuleDescriptorFromRegistry).trim()
          logger.debug("Module Descriptor: ${moduleDescriptor}")
          module.descriptor = [readJSON(text: moduleDescriptor)]

          // Upload Module Descriptor to Eureka Descriptor Registry
          sh(label: "Put Module Descriptor to Eureka Descriptor Registry",
            returnStdout: false,
            script: "curl -sS -X PUT ${Constants.EUREKA_REGISTRY_URL}${module.name}-${module.version} --data @- <<EOF\n${moduleDescriptor}\nEOF"
          )
        }
      }

      stage('[Git] Checkout module source') {
        // Skip Stage if module source is not Github
        if (!params.MODULE_SOURCE.contains('github')) {
          logger.info("Skip Git checkout stage for non-Github module source: ${params.MODULE_SOURCE}")
          Utils.markStageSkippedForConditional('[Git] Checkout module source')
        } else {
          checkout([
            $class           : 'GitSCM',
            branches         : [[name: "*/${featureBranch}"]],
            extensions       : [[$class: 'RelativeTargetDirectory', relativeTargetDir: module.name],
                                [$class: 'CleanBeforeCheckout', deleteUntrackedNestedRepositories: true],
                                [$class: 'AuthorInChangelog'],
                                [$class: 'SubmoduleOption', recursiveSubmodules: true]],
            userRemoteConfigs: [[url: "https://github.com/folio-org/${module.name}.git"]]
          ])
        }
      }

      stage('[Maven/Gradle] Compile') {
        // Skip Stage if module source is not Github
        if (!params.MODULE_SOURCE.contains('github')) {
          logger.info("Skip Maven/Gradle build stage for non-Github module source: ${params.MODULE_SOURCE}")
          Utils.markStageSkippedForConditional('[Maven/Gradle] Compile')
        } else {
          // Compile Application Module from Source Code
          deployAppModule.compile(logger, module, featureHash, mavenArguments, commonGradleOpts)
        }
      }

      stage('[Docker] Build and push') {
        // Skip Stage if module source is not Github
        if (!params.MODULE_SOURCE.contains('github')) {
          logger.info("Skip Docker build stage for non-Github module source: ${params.MODULE_SOURCE}")
          Utils.markStageSkippedForConditional('[Docker] Build and push')
        } else {
          // Put Container Image to Amazon ECR
          deployAppModule.buildAndPushContainerImage(logger, module)
        }
      }

      stage('[CURL] Upload module descriptor') {
        // Skip Stage if module source is not Github
        if (!params.MODULE_SOURCE.contains('github')) {
          logger.info("Skip Docker build stage for non-Github module source: ${params.MODULE_SOURCE}")
          Utils.markStageSkippedForConditional('[Docker] Build and push')
        } else {
          // Put Module Descriptor to Application Module Registry
          deployAppModule.putModuleDescriptorToRegistry(logger, module)
        }
      }

      stage('[REST] Get Tenants with Module') {
        deployAppModule.getTenantsWithModule(logger, eureka, module, namespace)
      }

      stage("Retrieve module's sidecar"){
        folioHelm.withKubeConfig(namespace.getClusterName()) {
          String sidecarImage =
            kubectl.getDeploymentContainerImageName(namespace.getNamespaceName(), module.getName(), "sidecar")
              .replace(':', '-')

          // In case updated module doesn't have Sidecar let's seek for it in other modules.
          if(!sidecarImage) {
            EurekaModule moduleWithSidecar = namespace.modules.getInstallJsonObject().find { fetchedModule ->
              kubectl.getDeploymentContainerImageName(namespace.getNamespaceName(), fetchedModule.getName(), "sidecar")
                      .replace(':', '-')
            }

            sidecarImage =
              kubectl.getDeploymentContainerImageName(namespace.getNamespaceName(), moduleWithSidecar.getName(), "sidecar")
                      .replace(':', '-')
          }

          if(sidecarImage)
            namespace.modules.addModule(sidecarImage)
          else
            throw new AbortException('There are no modules with sidecar in the namespace')
        }

        logger.debug("Configured Tenants: ${namespace.tenants}")
        logger.debug("Configured Applications: ${namespace.applications}")
        logger.debug("Namespace modules: ${namespace.modules.installJsonObject}")
      }

      stage('Update Module Version') {
        deployAppModule.updateModuleVersionFlow(logger, eureka, module, namespace)
      }

    } catch (e) {
      logger.warning("Caught exception: ${e}")
      error(e.getMessage())
    } finally {
      stage('Cleanup') {
        logger.debug("Workspace size: ${sh(returnStdout: true, script: 'du -sh .').trim()}")
        cleanWs notFailBuild: true
      }
    }
  }
}
