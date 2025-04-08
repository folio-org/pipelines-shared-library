import org.folio.Constants
import org.folio.models.EurekaNamespace
import org.folio.rest_v2.eureka.Eureka
import org.folio.rest_v2.eureka.kong.Tenants
import org.folio.utilities.Logger
import org.folio.models.module.EurekaModule

///**
// * Compile Application Module with Maven
// * @param logger Logger object to log messages
// * @param module EurekaModule object representing the Application Module
// * @param featureHash Commit hash of the feature branch
// * @param mavenArguments Maven arguments to be used for the build
// */
//def compileMaven(Logger logger, EurekaModule module, String featureHash, String mavenArguments) {
//  logger.info("Maven build tool is used")
//  module.buildDir = "${env.WORKSPACE}/${module.name}"
//
//  dir(module.buildDir) {
//    // Read module version from ./pom.xml
//    String moduleVersion = readMavenPom(file: 'pom.xml').version
//
//    // Load Module Details for Maven build
//    module.loadModuleDetails("${module.name}-${moduleVersion}.${featureHash}")
//
//    // Build Module as Maven Project
//    withMaven(
//      jdk: "${common.selectJavaBasedOnAgent(params.AGENT)}".toString(),
//      maven: Constants.MAVEN_TOOL_NAME,
//      traceability: false,
//      options: [artifactsPublisher(disabled: true)])
//      {
//        sh(
//          label: 'Build with Maven', script: """
//            mvn versions:set -DnewVersion=${module.getVersion()}
//            mvn package ${mavenArguments}
//          """.stripIndent().trim()
//        )
//      }
//  }
//}
//
///**
// * Compile Application Module with Gradle
// * @param logger Logger object to log messages
// * @param module EurekaModule object representing the Application Module
// * @param featureHash Commit hash of the feature branch
// * @param commonGradleOpts Common Gradle options to be used for the build
// */
//def compileGradle(Logger logger, EurekaModule module, String featureHash, String commonGradleOpts) {
//  logger.info("Gradle build tool is used")
//  module.buildDir = "${env.WORKSPACE}/${module.name}/service"
//
//  dir(module.buildDir) {
//    // Read module version from gradle.properties
//    String moduleVersion = readProperties(file: "gradle.properties")['appVersion']
//
//    // Append -SNAPSHOT to the version if it's not a snapshot version
//    if(!moduleVersion.contains("-SNAPSHOT")) {
//      moduleVersion += "-SNAPSHOT"
//    }
//
//    // Load Module Details for Gradle builds
//    module.loadModuleDetails("${module.name}-${moduleVersion}.${featureHash}")
//
//    // Build Module as Gradle Project
//    sh(label: 'Build with Gradle', script: "./gradlew ${commonGradleOpts} -PappVersion=${module.getVersion()} assemble")
//  }
//}
//
///**
// * Compile Application Module
// * @param logger Logger object to log messages
// * @param module EurekaModule object representing the Application Module
// * @param featureHash Commit hash of the feature branch
// * @param mavenArguments Maven arguments to be used for the build
// * @param commonGradleOpts Common Gradle options to be used for the build
// */
//def compile(Logger logger, EurekaModule module, String featureHash, String mavenArguments, String commonGradleOpts) {
//  // Check if Maven or Gradle build tool is used
//  module.buildTool = fileExists("${module.name}/pom.xml") ? 'maven' : 'gradle'
//
//  switch (module.buildTool) {
//    case 'maven':
//      compileMaven(logger, module, featureHash, mavenArguments)
//      break
//
//    case 'gradle':
//      compileGradle(logger, module, featureHash, commonGradleOpts)
//      break
//
//    default:
//      throw new RuntimeException("Unsupported build tool for Eureka module: ${module.buildTool}")
//  }
//}

/**
 * Build Container Image with Application Module and Push to AWS ECR Container Registry
 * @param logger Logger object to log messages
 * @param module EurekaModule object representing the Application Module
 */
def buildAndPushContainerImage(Logger logger, EurekaModule module) {
  logger.info('Building Container Image with Application Module')

  // Connect to expected Container Image Registry (Amazon ECR)
  docker.withRegistry("https://${Constants.ECR_FOLIO_REPOSITORY}", "ecr:${Constants.AWS_REGION}:${Constants.ECR_FOLIO_REPOSITORY_CREDENTIALS_ID}") {
    // Check if ECR Repository exists, if not create it
    common.checkEcrRepoExistence(module.name)

    dir(module.buildDir) {
      switch (module.buildTool) {
        case 'maven':
          module.modDescriptorPath = "${module.buildDir}/target/ModuleDescriptor.json"

          // Build Container Image
          docker.build("${module.name}:${module.version}", '--no-cache=true --pull=true .')

          // Push Container Image to Amazon ECR
          docker.image("${module.name}:${module.version}").push()
          break

        case 'gradle':
          module.modDescriptorPath = "${module.buildDir}/build/resources/main/okapi/ModuleDescriptor.json"

          // Build Container Image
          String shellCmd = """
            echo '[INFO] Build Container Image'
            ./gradlew ${commonGradleOpts} -PdockerRepo=folioci -PappVersion=${module.version} buildImage

            echo '[INFO] Rename Local Container Image'
            docker tag folioci/${module.name}:${module.version}.${env.BUILD_NUMBER} ${module.name}:${module.version}

            echo '[INFO] Get rid of BUILD_NUMBER in Module Descriptor'
            [ -e ${module.modDescriptorPath} ] && sed -i 's#${module.version}.${env.BUILD_NUMBER}#${module.version}#g' ${module.modDescriptorPath}
          """.stripIndent().trim()

          sh(label: 'Build Container Image', script: shellCmd)

          // Push Container Image to Amazon ECR
          docker.image("${module.name}:${module.version}").push()
          break

        default:
          throw new RuntimeException("Unsupported build tool for Eureka module: ${module.buildTool}")
      }
    }
  }
}

/**
 * Put Module Descriptor to Application Module Registry
 * @param logger Logger object to log messages
 * @param module EurekaModule object representing the Application Module
 */
def putModuleDescriptorToRegistry(Logger logger, EurekaModule module) {
  logger.info('Putting Module Descriptor to Application Module Registry')

  // Save Module Descriptor to Module instance for further usage
  module.descriptor = [readJSON(file: module.modDescriptorPath)]

  // Upload Module Descriptor to Eureka Module Registry
  sh(
    label: "Upload Module Descriptor to Module Registry",
    returnStdout: false,
    script: "[ -e ${module.modDescriptorPath} ] && curl -sS -X PUT ${Constants.EUREKA_REGISTRY_URL}${module.name}-${module.version} --upload-file ${module.modDescriptorPath}"
  )
}

/**
 * Get Application Tenants with Module
 * @param logger Logger object to log messages
 * @param eureka Eureka object representing the Eureka Registry
 * @param module EurekaModule object representing the Application Module
 * @param namespace EurekaNamespace object representing the Application Namespace
 */
def getTenantsWithModule(Logger logger, Eureka eureka, EurekaModule module, EurekaNamespace namespace) {
  logger.info('Getting Application Tenants with Module')

  eureka.getExistedTenantsForModule("${namespace.getClusterName()}-${namespace.getNamespaceName()}", module.getName())
          .values().each {namespace.addTenant(it)}

  namespace.tenants.values().each {it.getModules().addModule(module)}

  if(!(namespace.tenants))
    throw new RuntimeException("There are no tenants with module ${module.getName()}")
}

/**
 * Update Application Module Version Flow on every Tenant
 * @param logger Logger object to log messages
 * @param eureka Eureka object representing the Eureka Registry
 * @param module EurekaModule object representing the Application Module
 * @param namespace EurekaNamespace object representing the Application Namespace
 */
def updateModuleVersionFlow(Logger logger, Eureka eureka, EurekaModule module, EurekaNamespace namespace) {
  // Updated Applications Info Map<AppName, AppID>
  Map<String, String> updatedAppInfoMap = [:]

  try {
    logger.info('Updating Application Descriptor with New Module Version')
    updatedAppInfoMap = eureka.updateAppDescriptorFlow(namespace.applications, module)

    logger.info('Performing Module Discovery for New Module Version')
    eureka.registerModulesFlow([ module ])

    // Deploy Application Module with Helm
    deployAppModuleHelm(logger, eureka, module, namespace, updatedAppInfoMap)

    logger.info('Enabling Application Descriptor with Updated Module Version for Tenants in Namespace')
    namespace.getTenants().values().each {tenant ->
      Tenants.get(eureka.getKong()).updateApplications(tenant, updatedAppInfoMap.values().toList())
    }

    logger.info('Removing Stale Application Descriptor after Module Update')
    eureka.removeStaleResourcesFlow(namespace.applications, updatedAppInfoMap, module)

  } catch (e) {
    logger.warning("Failed to deploy New Module Version: ${e}")
    eureka.removeResourcesOnFailFlow(updatedAppInfoMap, module)
    throw e
  }
}

/**
 * Deploy Application Module with Helm
 * @param logger Logger object to log messages
 * @param eureka Eureka object representing the Eureka Registry
 * @param module EurekaModule object representing the Application Module
 * @param namespace EurekaNamespace object representing the Application Namespace
 * @param updatedAppInfoMap Updated Applications Info Map<AppName, AppID>
 * @param sleepTime Time in minutes to wait for the module to be initialized
 */
def deployAppModuleHelm(Logger logger, Eureka eureka, EurekaModule module, EurekaNamespace namespace, Map<String, String> updatedAppInfoMap, int sleepTime = 1) {
  try {
    logger.info('Deploying new Application Module Version with Helm')

    folioHelm.withKubeConfig(namespace.clusterName) {
      folioHelm.deployFolioModule(namespace, module.name, module.version, false, namespace.defaultTenantId)
      logger.info("Module ${module.name}-${module.version} deployed to ${namespace.clusterName} namespace ${namespace.workspaceName}")

      kubectl.checkDeploymentStatus(module.name, namespace.namespaceName, "300")
      logger.info("Waiting another ${sleepTime} minutes to get module initialized...")
      sleep time: sleepTime, unit: 'MINUTES'
    }
  } catch (e) {
    logger.warning("Failed to upgrade Helm Release with New Module Version: ${e}")
    throw e
  }
}
