import groovy.json.JsonOutput
import org.folio.utilities.Logger


Logger logger = new Logger(this, 'folioEurekaApp')

void generateApplicationDescriptorFile() {
  Logger logger = new Logger(this, 'folioEurekaApp')

  String platformComplete ='app-platform-complete'
  String platformMinimal= 'app-platform-minimal'

  logger.info("Going to build application descriptor for ${platformMinimal}")
  folioEurekaApp.applicationDescriptorFileGenerator(platformMinimal)
  logger.info("Going to build application descriptor for ${platformComplete}")
  folioEurekaApp.applicationDescriptorFileGenerator(platformComplete)

  }

  def applicationDescriptorFileGenerator(applicationId){

    Logger logger = new Logger(this, 'folioEurekaApp')
    String mdrBucket = "eureka-application-registry"

    sh(script: "git clone -b master --single-branch ${org.folio.Constants.FOLIO_GITHUB_URL}/${applicationId}.git")
    dir(applicationId) {
      awscli.withAwsClient() {
        sh(script: "mvn clean install -U -e -DbuildNumber=${BUILD_NUMBER} -DbeRegistries=\"s3::${mdrBucket}::descriptors/\" -DawsRegion=us-west-2")
      }
      dir('target') {
        sh(script: "ls -la")
        def applicationDescriptorFilename = sh(script: "find . -name '${applicationId}*.json' | head -1", returnStdout: true)
        try {
          sh(script: "curl ${org.folio.Constants.EUREKA_APPLICATIONS_URL} --upload-file ${applicationDescriptorFilename}")
          logger.info("File ${applicationDescriptorFilename} successfully uploaded to: ${org.folio.Constants.EUREKA_APPLICATIONS_URL}")
        } catch (Exception e) {
          logger.warning("Failed to generat application descriptor\nError: ${e.getMessage()}")
        }
      }
    }
  }

//def getApplicationDescriptor(String applicationId) {
//  try {
//    String response = steps.sh(script: "curl ${org.folio.Constants.EUREKA_APPLICATIONS_URL}${applicationId}", returnStdout: true)
//    logger.info("Application descriptor: ${response}")
//    return response
//
//  } catch (Exception e) {
//    logger.warning("Failed to get applicationDescriptor ${applicationId}\nError: ${e.getMessage()}")
//  }
//}
//
//def listApplicationDescriptors() {
//  try {
//
//    String response = steps.sh(script: "curl ${org.folio.Constants.EUREKA_APPLICATIONS_URL}", returnStdout: true)
//    logger.info("Application descriptors list:" JsonOutput.prettyPrint(JsonOutput.toJson(response)))
//    return response
//
//  } catch (Exception e) {
//    logger.warning("Failed to get application dscriptors \nError: ${e.getMessage()}")
//  }
//}
//
//def deleteApplicationDescriptor(String applicationId) {
//  try {
//
//    String response = steps.sh(script: "curl -X DELETE${org.folio.Constants.EUREKA_APPLICATIONS_URL}${applicationId}", returnStdout: true)
//    logger.info("Application descriptor deleted:" JsonOutput.prettyPrint(JsonOutput.toJson(response)))
//    return response
//
//  } catch (Exception e) {
//    logger.warning("Failed to get application dscriptors \nError: ${e.getMessage()}")
//  }
//}
