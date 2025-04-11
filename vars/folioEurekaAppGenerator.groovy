import groovy.transform.Field
import org.folio.Constants
import org.folio.models.FolioInstallJson
import org.folio.utilities.Logger
import org.folio.utilities.Tools

@Field
Logger logger = new Logger(this, 'folioEurekaAppGenerator')

Map updateDescriptor(Map descriptor, FolioInstallJson moduleList, boolean debug = false) {
  logger.info("Updating application descriptor...")

  String appName = descriptor.name
  String oldVersion = descriptor.version

  dir(appName) {
    writeJSON file: appName + "-" + oldVersion + '.json', json: descriptor
    new Tools(this).copyResourceFileToCurrentDirectory("applications/generator/pom.xml")

    String beModulesString = (moduleList.getBackendModules() + moduleList.getEdgeModules()).collect { "${it.getId()}" }.join(",")
    String uiModulesString = moduleList.getUiModules().collect { "${it.getId()}" }.join(",")

    return _generate(appName, debug, "org.folio:folio-application-generator:updateFromJson"
      , "-DappDescriptorPath=${appName}-${oldVersion}.json -Dmodules='${beModulesString}' -DuiModules='${uiModulesString}'")
  }
}

Map generateFromDescriptor(Map descriptor, FolioInstallJson moduleList, String version = null, boolean debug = false){
  logger.info("Generating application descriptor from descriptor...")

  Map template = createTemplateFromDescriptor(descriptor, version, debug)
  String appName = template.name

  dir(appName) {
    if(moduleList) {
      template = updateTemplate(template, moduleList, debug, true)
    }

    writeJSON file: appName + '.template.json', json: template
    new Tools(this).copyResourceFileToCurrentDirectory("applications/generator/pom.xml")

    return _generate(appName, debug)
  }
}

Map createTemplateFromDescriptor(Map descriptor, String version = null, boolean debug = false) {
  Map template = descriptor

  template.remove('id')
  template.remove('moduleDescriptors')
  template.remove('uiModuleDescriptors')
  template.version = version ?: template.version
  template.id = "${template.name}-${template.version}"

  template.modules.each { module ->
    (module as Map).remove('id')
  }

  template.uiModules.each { module ->
    (module as Map).remove('id')
  }

  if(debug)
    logger.debug("Generated template from descriptor:\n $template")

  return template
}

Map generateFromRepository(String repoName, FolioInstallJson moduleList, String branch = "master", boolean debug = false) {
  logger.info("Generating application descriptor from repository...")

  //  sh(script: "rm -rf ${appName} || true && git clone --branch ${branch} --single-branch ${Constants.FOLIO_GITHUB_URL}/${appName}.git")

  checkout([
    $class           : 'GitSCM',
    branches         : [[name: "*/${branch}"]],
    extensions       : [[$class: 'RelativeTargetDirectory', relativeTargetDir: appName],
                        [$class: 'CleanBeforeCheckout', deleteUntrackedNestedRepositories: true],
                        [$class: 'AuthorInChangelog'],
                        [$class: 'SubmoduleOption', recursiveSubmodules: true]],
    userRemoteConfigs: [[url: "${Constants.FOLIO_GITHUB_URL}/${appName}.git"]]
  ])

  dir(repoName) {
    if(moduleList) {
      Map updatedTemplate = updateTemplate(readJSON(file: repoName + ".template.json"), moduleList, debug)
      writeJSON file: repoName + ".template.json", json: updatedTemplate
    }

    return _generate(repoName, debug)
  }
}

private Map _generate(String appName, boolean debug = false, String command = "org.folio:folio-application-generator:generateFromJson", String args = "") {
  awscli.withAwsClient() {
    withMaven(
      jdk: "${common.selectJavaBasedOnAgent(params.AGENT)}".toString(),
      maven: Constants.MAVEN_TOOL_NAME,
      traceability: false,
      options: [artifactsPublisher(disabled: true)]
    ) {
      sh """
            mvn clean ${command} -U -e \
            -DbuildNumber=${BUILD_NUMBER} \
            -Dregistries='okapi::${org.folio.rest_v2.Constants.OKAPI_REGISTRY},s3::eureka-application-registry::descriptors' \
            ${args} -DawsRegion=us-west-2
          """.stripIndent()
    }
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

Map updateTemplate(def template, FolioInstallJson moduleList, boolean debug = false, boolean onlyLatest = true) {
  if (debug) {
    logger.info("Module Map with all modules and exact version:\n$moduleList")
    logger.info("Default template backend module list:\n$template.modules")
  }

  template.modules = replaceModuleVersions(template.modules, moduleList, onlyLatest)

  if (debug) {
    logger.info("Updated backend module list with latest version:\n$template.modules")

    logger.info("""
      Default template UI module list:
      $template.uiModules
      """)
  }

  template.uiModules = replaceModuleVersions(template.uiModules, moduleList, onlyLatest)

  if (debug) {
    logger.info("""
      Updated UI module list with latest version:
      $template.uiModules
      """)
  }

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
