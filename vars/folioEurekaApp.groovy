import org.folio.Constants
import org.folio.utilities.Logger
import groovy.json.JsonSlurperClassic
import groovy.json.JsonOutput


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

// Ensure JsonSlurperClassic is used locally and not captured in a closure
      def appFileContent = readFile(file: appDescriptorFilename)
      def appParsedJson = new JsonSlurperClassic().parseText(appFileContent)

      // Modify the JSON data
      if (appParsedJson.containsKey('modules')) {
        appParsedJson['modules'].each { module ->
          module.version = module.version.replaceAll(/(\.\d+)$/, "")
        }
      }

      // Write the modified content back to the file
      def modifiedAppJsonContent = JsonOutput.prettyPrint(JsonOutput.toJson(appParsedJson))
      writeFile(file: appDescriptorFilename, text: modifiedAppJsonContent)

      // Verify the changes
      def modifiedContent = sh(script: "cat ${appDescriptorFilename}", returnStdout: true).trim()
      println "Modified file content:\n${modifiedContent}"

//      def fileContent = readFile(file: applicationDescriptorFilename)

//      def data = new JsonSlurperClassic().parseText(fileContent)
//      if (data.containsKey('modules')) {
//        data['modules'].each { module ->
//          if (module.containsKey('version')) {
//            module['version'] = module['version'].toString().replaceAll(/(\.\d+)$/, "")
//            println("Modified module: ${module}")
//          } else {
//            println("Module does not contain 'version': ${module}")
//          }
//        }
//      } else {
//        println "The 'modules' key does not exist in the JSON data."
//      }
//      println(data)
//      def modifiedFileContent = JsonOutput.prettyPrint(JsonOutput.toJson(data))
//      writeFile file: applicationDescriptorFilename, text: modifiedFileContent
//      println(applicationDescriptorFilename)


//      try {
//        sh(script: "curl ${Constants.EUREKA_APPLICATIONS_URL} --upload-file ${applicationDescriptorFilename}")
//        logger.info("File ${applicationDescriptorFilename} successfully uploaded to: ${Constants.EUREKA_APPLICATIONS_URL}")
//        return readJSON(file: "${applicationDescriptorFilename}")
//      } catch (Exception e) {
//        println("Failed to generat application descriptor\nError: ${e.getMessage()}")
//      }
    }
  }
}
