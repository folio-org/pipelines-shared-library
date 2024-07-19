package org.folio.rest_v2

import org.folio.models.OkapiTenant
import org.folio.utilities.RequestException
import groovy.json.JsonSlurperClassic
import groovy.json.JsonOutput

class Eureka extends Authorization {

  public OkapiTenant superTenant

  Eureka(Object context, String okapiDomain, boolean debug = false) {
    super(context, okapiDomain, debug)
  }

  def getDescriptorsList(applicationId) {

    String bucketName = 'eureka-application-registry'
    steps.awscli.withAwsClient() {
      steps.sh(script: "aws s3api get-object --bucket ${bucketName} --key apps/${applicationId}.json ${applicationId}.json")
    }
    logger.info(steps.readJSON(file: "${applicationId}.json"))
    return steps.readJSON(file: "${applicationId}.json")
  }

  String getEurekaToken() {
    logger.info("Getting access token from Keycloak service")

    String url = "https://folio-eureka-veselka-keycloak.ci.folio.org/realms/master/protocol/openid-connect/token"
    Map<String, String> headers = [
      'Content-Type': 'application/x-www-form-urlencoded'
    ]
    String requestBody = "client_id=folio-backend-admin-client&client_secret=SecretPassword&grant_type=client_credentials"

    try {
      def response = restClient.post(url, requestBody, headers).body
      logger.info("Access token received successfully from Keycloak service")
      return response.access_token
    } catch (RequestException e) {
      if (e.statusCode == HttpURLConnection.HTTP_NOT_FOUND) {
        logger.info("Cant get token.")
      } else {
        throw new RequestException("Keycloak is unavailable", e.statusCode)
      }
    }
  }

  def isDiscoveryModulesRegistered(String applicationId, String modulesJson) {

    String url = generateKongUrl("/applications/${applicationId}/discovery?limit=500")
    def jsonSlurper = new JsonSlurperClassic()
    def modulesMap = jsonSlurper.parseText(modulesJson)

    def response = restClient.get(url)
    def content = response.body

    if (content.totalRecords == modulesMap.discovery.size()) {
      logger.info("All module discovery information are registered. Nothing to do.")
      return false
    } else if (content.totalRecords == 0) {
      logger.info("Any discovery modules is registerd. Proceeding with registration.")
      return null
    } else {
      logger.info("Not all modules discovery is registered. Proceeding with registration.")
      return content
    }
  }

  void registerApplicationDiscovery(String applicationId) {
    String descriptorsList = getDescriptorsList(applicationId)

    def jsonSlurper = new JsonSlurperClassic()
    def parsedJson = jsonSlurper.parseText(descriptorsList)
    def modules = parsedJson.modules

    modules.each { module ->
      module.location = "http://${module.name}:8082"
    }

    def modulesJson = ['discovery': modules]

    String modulesList = (JsonOutput.toJson(modulesJson))



    def result = isDiscoveryModulesRegistered(applicationId, modulesList)

    if (result == false) {
      logger.info("All modules are already registered. No further action needed.")
    } else if (result == null) {
      Map<String, String> headers = [
        'x-okapi-token': getEurekaToken(),
        'Content-Type' : 'application/json'
      ]
      String url = generateKongUrl("/modules/discovery")
      logger.info("Going to register modules\n ${modulesJson}")
      restClient.post(url, modulesList, headers).body

    } else {

      Map<String, String> headers = [
        'x-okapi-token': getEurekaToken(),
        'Content-Type' : 'application/json'
      ]
      modulesJson.discovery.each { modDiscovery ->

        String requestBody = JsonOutput.toJson(modDiscovery)

        try {
          String url = generateKongUrl("/modules/${modDiscovery.id}/discovery")
          restClient.post(url, requestBody, headers).body
          logger.info("Registered module discovery: ${modDiscovery.id}")
        } catch (RequestException e) {
          if (e.statusCode == HttpURLConnection.HTTP_CONFLICT) {
            logger.info("Module already registered (skipped): ${modDiscovery.id}")
          } else {
            throw new RequestException("Error registering module: ${modDiscovery.id}, error: ${e.statusCode}")
          }
        }
      }
    }
  }
}
