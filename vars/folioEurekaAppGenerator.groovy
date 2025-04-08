import groovy.transform.Field
import org.folio.Constants
import org.folio.models.FolioInstallJson
import org.folio.utilities.Logger

@Field
Logger logger = new Logger(this, 'folioEurekaAppGenerator')

Map changeVersion(Map descriptor, String version) {
  logger.info("Changing version of $descriptor.id to $version")

  descriptor.version = version.trim()
  descriptor.id = "${descriptor.name}-${version.trim()}"

  return descriptor
}

Map upgradeApplicationDescriptor(Map descriptor, FolioInstallJson moduleList, boolean debug = false) {
  def appDescriptor = generateApplicationDescriptor(appName, moduleList, branch, debug)

  if (appDescriptor) {
    logger.info("Application descriptor for $appName successfully generated")
  } else {
    logger.error("Failed to generate application descriptor for $appName")
  }

  return appDescriptor
}

Map generateApplicationDescriptor(String appName, FolioInstallJson moduleList, String branch = "master", boolean debug = false) {
  sh(script: "rm -rf ${appName} || true && git clone --branch ${branch} --single-branch ${Constants.FOLIO_GITHUB_URL}/${appName}.git")

  dir(appName) {
    def updatedTemplate = updateTemplate(readJSON(file: appName + ".template.json"), moduleList)
    writeJSON file: appName + ".template.json", json: updatedTemplate

    awscli.withAwsClient() {
      sh(script: "mvn clean install -U -e -DbuildNumber=${BUILD_NUMBER} -DawsRegion=us-west-2")

      logger.info("Application $appName successfuly generated")
    }

    dir('target') {
      sh(script: "ls -la")

      if(debug)
        logger.debug(""""Generated application:
          ${readJSON(file: sh(script: "find . -name '${appName}*.json' | head -1", returnStdout: true).trim())}
          """
        )

      return readJSON(file: sh(script: "find . -name '${appName}*.json' | head -1", returnStdout: true).trim())
    }
  }
}

def updateTemplate(def template, FolioInstallJson moduleList){
  logger.info("Updated template latest module version with exact value...")

  logger.info("""
  Module Map with all modules and exact version:
  $moduleList
  """)

  logger.info("""
  Default template backend module list:
  $template.modules
  """)

  template.modules = replaceModuleVersions(template.modules, moduleList)

  logger.info("""
  Updated backend module list with latest version:
  $template.modules
  """)

  logger.info("""
  Default template UI module list:
  $template.uiModules
  """)

  template.uiModules = replaceModuleVersions(template.uiModules, moduleList)

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
