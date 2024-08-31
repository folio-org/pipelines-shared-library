import groovy.transform.Field
import org.folio.Constants
import org.folio.utilities.Logger

@Field
Logger logger = new Logger(this, 'folioEurekaAppGenerator')


def generateApplicationDescriptor(String appName, Map<String, String> moduleList) {
  sh(script: "git clone -b master --single-branch ${Constants.FOLIO_GITHUB_URL}/${appName}.git")

  dir(appName) {
    def updatedTemplate = setTemplateModuleLatestVersion(readJSON(file: appName + ".template.json"), moduleList)
    writeJSON file: appName + ".template.json", json: updatedTemplate

    awscli.withAwsClient() {
      sh(script: "mvn clean install -U -e -DbuildNumber=${BUILD_NUMBER} -DawsRegion=us-west-2")

      logger.info("Application $appName successfuly generated")
    }

    dir('target') {
      sh(script: "ls -la")

      logger.debug("Generated application:")
      logger.info(readJSON(file: sh(script: "find . -name '${appName}*.json' | head -1", returnStdout: true).trim()))

      return readJSON(file: sh(script: "find . -name '${appName}*.json' | head -1", returnStdout: true).trim())
    }
  }
}

def setTemplateModuleLatestVersion(def template, Map<String, String> moduleList){
  logger.info("Updated template latest module version with exact value...")

  logger.info("""
  Module Map with all modules and exact version:
  $moduleList
  """)

  logger.info("""
  Default template backend module list:
  $template.modules
  """)

  template.modules = setModuleLatestVersion(template.modules, moduleList)

  logger.info("""
  Updated backend module list with latest version:
  $template.modules
  """)

  logger.info("""
  Default template UI module list:
  $template.uiModules
  """)

  template.uiModules = setModuleLatestVersion(template.uiModules, moduleList)

  logger.info("""
  Updated UI module list with latest version:
  $template.uiModules
  """)

  return template
}

def setModuleLatestVersion(def templateModules, Map<String, String> moduleList){
  logger.info("Updated latest module version with exact value...")

  def updatedModules = templateModules

  templateModules.eachWithIndex{module, index ->
    if(!moduleList[module.name])
      logger.info("Module Map with all modules and exact version doesn't contain $module.name")
    else
      updatedModules[index].version = module.version.trim() == "latest" ? moduleList[module.name] : module.version
  }

  return updatedModules
}
