import org.folio.Constants
import org.folio.utilities.Logger
import groovy.json.JsonSlurperClassic


Logger logger = new Logger(this, 'folioEurekaApp')

def generateAppPlatformMinimalDescriptor() {
  String platformMinimal = 'app-platform-minimal'

  logger.info("Going to build application descriptor for ${platformMinimal}")
  return  applicationDescriptorFileGenerator(platformMinimal)
}

def generateAppPlatformCompleteDescriptor() {
  String platformComplete = 'app-platform-complete'

  logger.info("Going to build application descriptor for ${platformComplete}")
  return applicationDescriptorFileGenerator(platformComplete)
}

def applicationDescriptorFileGenerator(String applicationId) {

  sh(script: "git clone -b master --single-branch ${Constants.FOLIO_GITHUB_URL}/${applicationId}.git")
  dir(applicationId) {
    awscli.withAwsClient() {
      sh(script: "mvn clean install -U -e -DbuildNumber=${BUILD_NUMBER} -DbeRegistries=\"s3::${Constants.EUREKA_MDR_BUCKET}::descriptors/\" -DawsRegion=us-west-2")
    }
    dir('target') {
      sh(script: "ls -la")
      def applicationDescriptorFilename = sh(script: "find . -name '${applicationId}*.json' | head -1", returnStdout: true).trim()

      def fileContent = readFile(file: applicationDescriptorFilename)
      def data = new JsonSlurperClassic().parseText(fileContent)
      //def data = new JsonSlurperClassic().parseText(new File("${applicationDescriptorFilename}").text)
//      data['modules'].each { id ->
//        id.each { module ->
//          module['version'] = module['version'].toString().replaceAll(/(\.\d+)$/, "")
//        }
        data['modules'].each { module ->
          if (module.containsKey('version')) {
            // Remove trailing numeric suffix after the first period
            module['version'] = module['version'].toString().replaceAll(/(\.\d+)(-\S+)?$/, "")
          } else {
            logger.warning("Module does not contain 'version': ${module}")
          }
        }
//        println(id)
      logger.warning("${data}")
//      try {
//        sh(script: "curl ${Constants.EUREKA_APPLICATIONS_URL} --upload-file ${applicationDescriptorFilename}")
//        logger.info("File ${applicationDescriptorFilename} successfully uploaded to: ${Constants.EUREKA_APPLICATIONS_URL}")
//        return readJSON(file: "${applicationDescriptorFilename}")
//      } catch (Exception e) {
//        logger.warning("Failed to generat application descriptor\nError: ${e.getMessage()}")
//      }
    }
  }
}
