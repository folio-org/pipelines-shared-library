import groovy.transform.Field
import org.folio.Constants
import org.folio.models.FolioInstallJson
import org.folio.utilities.Logger

@Field
Logger logger = new Logger(this, 'folioEurekaAppGenerator')

static Map createTemplateFromDescriptor(Map descriptor){
  Map template = descriptor

  template.remove('id')
  template.remove('moduleDescriptors')
  template.remove('uiModuleDescriptors')

  template.modules.each { module ->
    (module as Map).remove('id')
  }

  template.uiModules.each { module ->
    (module as Map).remove('id')
  }

  return template
}

Map updateDescriptor(Map descriptor, String version) {
  logger.info("Changing version of $descriptor.id to $version")

  descriptor.version = version.trim()
  descriptor.id = "${descriptor.name}-${version.trim()}"

  return descriptor
}

Map generateDescriptorFromDescriptor(){}

Map generateDescriptorFromTemplate(Map template, FolioInstallJson moduleList, String version = null, boolean debug = false) {
  logger.info("Generating application descriptor from template...")

  if(debug) logger.debug("Template: ${template}")

  Map updatedTemplate = template

  if (version) updatedTemplate.version = version

  updateTemplate(updatedTemplate, moduleList)

  sh(script: "rm -rf folio-application-generator || true && git clone --branch master --single-branch ${Constants.FOLIO_GITHUB_URL}/folio-application-generator.git")



  Map descriptor = createTemplateFromDescriptor(template)

  logger.info("Changing version of $descriptor.id to $version")

  descriptor = changeVersion(descriptor, version)

  logger.info("Changing name of $descriptor.id to $appName")

  descriptor.name = appName

  return descriptor
}

Map generateDescriptor(String appName, FolioInstallJson moduleList, String branch = "master", boolean debug = false) {
  checkout([
    $class           : 'GitSCM',
    branches         : [[name: "*/${branch}"]],
    extensions       : [[$class: 'RelativeTargetDirectory', relativeTargetDir: appName],
                        [$class: 'CleanBeforeCheckout', deleteUntrackedNestedRepositories: true],
                        [$class: 'AuthorInChangelog'],
                        [$class: 'SubmoduleOption', recursiveSubmodules: true]],
    userRemoteConfigs: [[url: "${Constants.FOLIO_GITHUB_URL}/${appName}.git"]]
  ])

//  sh(script: "rm -rf ${appName} || true && git clone --branch ${branch} --single-branch ${Constants.FOLIO_GITHUB_URL}/${appName}.git")

  dir(appName) {
    def updatedTemplate = updateTemplate(readJSON(file: appName + ".template.json"), moduleList)
    writeJSON file: appName + ".template.json", json: updatedTemplate

    awscli.withAwsClient() {
      withMaven(
        jdk: "${common.selectJavaBasedOnAgent(params.AGENT)}".toString(),
        maven: Constants.MAVEN_TOOL_NAME,
        traceability: false,
        options: [artifactsPublisher(disabled: true)]
      ) {
        sh """
              mvn clean compile -U -e \
              -DbuildNumber=${BUILD_NUMBER} \
              -Dregistries='okapi::${org.folio.rest_v2.Constants.OKAPI_REGISTRY},s3::eureka-application-registry::descriptors' \
              -DawsRegion=us-west-2
            """.stripIndent()
      }

//      sh(script: "mvn clean compile -U -e -DbuildNumber=${BUILD_NUMBER} -Dregistries='${org.folio.rest_v2.Constants.OKAPI_REGISTRY}' -DawsRegion=us-west-2")

      logger.info("Application $appName successfuly generated")
    }

    dir('target') {
      if(debug) {
        sh(script: "ls -la")

        logger.debug(""""Generated application:
          ${readJSON(file: sh(script: "find . -name '${appName}*.json' | head -1", returnStdout: true).trim())}
          """
        )
      }

      return readJSON(file: sh(script: "find . -name '${appName}*.json' | head -1", returnStdout: true).trim())
    }
  }
}

Map generate(String appName, FolioInstallJson moduleList, String branch = "master", boolean debug = false){}

def updateTemplate(def template, FolioInstallJson moduleList, boolean onlyLatest = true, String appVersion = null, boolean debug = false) {
  if(debug) {
    logger.info("Module Map with all modules and exact version:\n$moduleList")
    logger.info("Default template backend module list:\n$template.modules")
  }

  template.modules = replaceModuleVersions(template.modules, moduleList, onlyLatest)

  if(debug) {
    logger.info("Updated backend module list with latest version:\n$template.modules")

    logger.info("""
  Default template UI module list:
  $template.uiModules
  """)
  }

  template.uiModules = replaceModuleVersions(template.uiModules, moduleList, onlyLatest)

  logger.info("""
  Updated UI module list with latest version:
  $template.uiModules
  """)

  return template
}

def replaceModuleVersions(def templateModules, FolioInstallJson moduleList, boolean onlyLatest = true) {
  logger.info("Updated latest module version with exact value...")

  def updatedModules = templateModules

  templateModules.eachWithIndex{module, index ->
    if(!moduleList.getModuleByName(module.name))
      logger.info("Install JSON doesn't contain $module.name")
    else
      updatedModules[index].version = module.version.trim() != "latest" && onlyLatest ? module.version : moduleList.getModuleByName(module.name)
  }

  return updatedModules
}

Map changeApplicationVersion(Map descriptor, String version) {
  logger.info("Changing version of $descriptor.id to $version")

  descriptor.version = version.trim()
  descriptor.id = "${descriptor.name}-${version.trim()}"

  return descriptor
}
