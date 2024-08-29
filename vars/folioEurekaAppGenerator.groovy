import org.folio.Constants
import org.folio.utilities.Logger

Logger logger = new Logger(this, 'folioEurekaAppGenerator')

def generateApplicationDescriptor(String appName, Map<String, String> moduleList) {
  sh(script: "git clone -b master --single-branch ${Constants.FOLIO_GITHUB_URL}/${appName}.git")

  dir(appName) {
    def updatedTemplate = setModuleLatestVersion(readJSON(file: appName + ".template.json"), moduleList)
    writeJSON file: appName + ".template.json", json: updatedTemplate

    awscli.withAwsClient() {
      sh(script: "mvn clean install -U -e -DbuildNumber=${BUILD_NUMBER} -DawsRegion=us-west-2")

      logger.info("Application $appName successfuly generated")
    }

    dir('target') {
      sh(script: "ls -la")

      return readJSON(file: sh(script: "find . -name '${appName}*.json' | head -1", returnStdout: true).trim())
    }
  }
}

def setModuleLatestVersion(def template, Map<String, String> moduleList){
  logger.info("Updated template latest module version with exact value...0")

  List updatedModules = template.modules

  logger.info("""
  Default template module list:
  $updatedModules
  """)

  logger.info("""
  Module Map with all modules and exact version:
  $updatedModules
  """)

  template.modules.each{index, module ->
    if(!moduleList[module.name])
      logger.info("Module Map with all modules and exact version doesn't contain $module.name")
    else
      updatedModules[index].version = module.version.trim() == "latest" ? moduleList[module.name].version : module.version
  }

  logger.info("""
  Updated module list with latest version:
  $updatedModules
  """)

  template.modules = updatedModules

  return template
}
