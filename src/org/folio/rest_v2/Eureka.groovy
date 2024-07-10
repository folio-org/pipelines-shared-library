package org.folio.rest_v2

import groovy.json.JsonOutput
import org.folio.models.OkapiTenant
import org.folio.utilities.RequestException

class Eureka extends Authorization {

  public OkapiTenant superTenant

  Eureka(Object context, String okapiDomain, OkapiTenant superTenant, boolean debug = false) {
    super(context, okapiDomain, debug)
  }

  def getApplicationDescriptor(String applicationId) {
    try {
      String response = steps.sh(script: "curl ${org.folio.Constants.EUREKA_APPLICATIONS_URL}${applicationId}", returnStdout: true)
      logger.info("Application descriptor: ${response}")
      return response

    } catch (Exception e) {
    logger.warning("Failed to get applicationDescriptor ${applicationId}\nError: ${e.getMessage()}")
    }
  }

  def listApplicationDescriptors() {
    try {

      String response = steps.sh(script: "curl ${org.folio.Constants.EUREKA_APPLICATIONS_URL}", returnStdout: true)
      logger.info("Application descriptors list:" JsonOutput.prettyPrint(JsonOutput.toJson(response)))
      return response

    } catch (Exception e) {
      logger.warning("Failed to get application dscriptors \nError: ${e.getMessage()}")
    }
  }

  def deleteApplicationDescriptor(String applicationId) {
    try {

      String response = steps.sh(script: "curl -X DELETE${org.folio.Constants.EUREKA_APPLICATIONS_URL}${applicationId}", returnStdout: true)
      logger.info("Application descriptor deleted:" JsonOutput.prettyPrint(JsonOutput.toJson(response)))
      return response

    } catch (Exception e) {
      logger.warning("Failed to get application dscriptors \nError: ${e.getMessage()}")
    }
  }

  void generateApplicationDescriptorFile(applicationId) {

    def publicMdr = "https://folio-registry.dev.folio.org"

    logger.info("Going to build application descriptor for ${applicationId}")

    steps.sh(script: "git clone -b master --single-branch ${org.folio.Constants.FOLIO_GITHUB_URL}/${applicationId}.git")
    steps.dir(applicationId)
    steps.sh(script: "mvn clean install -U DbuildNumber=${BUILD_NUMBER} -DbeRegistries=\"${org.folio.Constants.EUREKA_REGISTRY_URL},${org.folio.Constants.EUREKA_REGISTRY_URL}/eureka/\" -DuiRegistries=\"okapi::${publicMdr}\" -DoverrideConfigRegistries=true")

    def applicationDescriptorFilename = steps.sh(script: "ls -1t | head -1", returnStdout: true)
    def applicationDescriptorFilePath = "target/${applicationDescriptorFilename}"

    try {
      steps.sh(script: "curl ${org.folio.Constants.EUREKA_APPLICATIONS_URL} --upload-file ${applicationDescriptorFilePath}")
      logger.info("File ${applicationDescriptorFilePath} successfully uploaded to: ${org.folio.Constants.EUREKA_APPLICATIONS_URL}")
      logger.info("Application descriptor deleted:" JsonOutput.prettyPrint(JsonOutput.toJson(applicationDescriptorFilename)))
    } catch (Exception e) {
      logger.warning("Failed to generat application descriptor\nError: ${e.getMessage()}")
    }
  }
}
