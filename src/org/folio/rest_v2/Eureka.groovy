package org.folio.rest_v2

import org.folio.models.OkapiTenant
import org.folio.utilities.RequestException
import groovy.json.JsonSlurper
import groovy.json.JsonOutput

class Eureka extends Authorization {

  public OkapiTenant superTenant

  Eureka(Object context, String okapiDomain, boolean debug = false) {
    super(context, okapiDomain, debug)
  }

//  boolean isApplicationRegistered(String applicationId) {
//
//    String url = generateKongUrl("/applications/${applicationId}")
//
//    try {
//      restClient.get(url).body
//      logger.info("Application ${applicationId} is already registered.")
//      return true
//    } catch (RequestException e) {
//      if (e.statusCode == HttpURLConnection.HTTP_NOT_FOUND) {
//        logger.info("Application id ${applicationId} not found in Application manager. Proceeding with registration.")
//        return false
//      } else {
//        throw new RequestException("Application manager is unavailable", e.statusCode)
//      }
//    }
//  }
//
  def getDescriptorsList(applicationId) {

    String bucketName = 'eureka-application-registry'
    steps.awscli.withAwsClient() {
      steps.sh(script: "aws s3api get-object --bucket ${bucketName} --key apps/${applicationId}.json ${applicationId}.json")
    }
    logger.info(steps.readJSON(file: "${applicationId}.json"))
    return steps.readJSON(file: "${applicationId}.json")
  }
//
//  String getEurekaToken(String client_id, String client_secret, String grant_type) {
//    logger.info("Getting access token from Keycloak service")
//
//    String url = "https://folio-eureka-scout-keycloak.ci.folio.org/realms/master/protocol/openid-connect/token"
//    Map<String,String> headers = [
//      'Content-Type':'application/x-www-form-urlencoded'
//    ]
//    String requestBody = "client_id=$client_id&client_secret=$client_secret&grant_type=$grant_type"
//
//    try {
//      def response = restClient.post(url, requestBody, headers).body
//      logger.info("Access token received successfully from Keycloak service")
//      logger.info("${response.access_token}")
//      return response.access_token
//    } catch (RequestException e) {
//      if (e.statusCode == HttpURLConnection.HTTP_NOT_FOUND) {
//        logger.info("Cant get token.")
//      } else {
//        throw new RequestException("Keycloak is unavailable", e.statusCode)
//      }
//    }
//  }
//
//  def registerApplication(String applicationId) {
//    String descriptorsList = getDescriptorsList(applicationId)
//    if (isApplicationRegistered(applicationId)) {
//      logger.warning("Application ${applicationId} is already registered.")
//      return
//    }
//
//    String url = "https://folio-eureka-scout-kong.ci.folio.org/applications?check=false"
//    Map<String,String> headers = [
//        'x-okapi-token': getEurekaToken(),
//        'Content-Type': 'application/json'
//      ]
//    try {
//    restClient.post(url, descriptorsList, headers)
//    logger.info("Application registered: ${descriptorsList}")
//    } catch (RequestException e) {
//        throw new RequestException("Application is not registered", e.statusCode)
//      }
//    }

  String getEurekaToken() {
    logger.info("Getting access token from Keycloak service")

    String url = "https://folio-eureka-scout-keycloak.ci.folio.org/realms/master/protocol/openid-connect/token"
    Map<String, String> headers = [
      'Content-Type': 'application/x-www-form-urlencoded'
    ]
    String requestBody = "client_id=folio-backend-admin-client&client_secret=SecretPassword&grant_type=client_credentials"

    try {
      def response = restClient.post(url, requestBody, headers).body
      logger.info("Access token received successfully from Keycloak service")
      logger.info("${response.access_token}")
      return response.access_token
    } catch (RequestException e) {
      if (e.statusCode == HttpURLConnection.HTTP_NOT_FOUND) {
        logger.info("Cant get token.")
      } else {
        throw new RequestException("Keycloak is unavailable", e.statusCode)
      }
    }
  }

  boolean isDiscoveryModulesRegistered(String applicationId, String modulesJson) {

    String url = generateKongUrl("/applications/${applicationId}/discovery?limit=500")

    def response = restClient.get(url)
    def content = response.body
    logger.warning("Do comparison")
    if (content == modulesJson) {
      logger.info("All module discovery information are registered. Nothing to do.")
      return false
    } else {
      logger.info("Not all module discovery information is registered. Proceeding with registration.")
      return true
    }
  }

//  boolean isDiscoveryModulesRegistered(List descriptorsList, OkapiTenant tenant) {
//
//    String url = generateUrl("/modules/discovery")
//    Map<String, String> headers = getAuthorizedHeaders(tenant)
//
//    try {
//      restClient.post(url, descriptorsList, headers)
//      logger.info("Application discovery registered: ${url}")
//      return true
//    } catch (RequestException e) {
//      if (e.statusCode == !HttpURLConnection.HTTP_CREATED) {
//        logger.info("Not all of the module discovery information is registered. Going to register one by one")
//        return false
//      } else {
//        throw new RequestException("Discovery modules is unavailable", e.statusCode)
//      }
//    }
//  }
  void registerApplicationDiscovery(String applicationId) {
    String descriptorsList = getDescriptorsList(applicationId)

    def jsonSlurper = new JsonSlurper()
    def parsedJson = jsonSlurper.parseText(descriptorsList)
    def modules = parsedJson.modules

    modules.each { module ->
      module.location = "https://folio-eureka-scout-kong.ci.folio.org:8082/${module.name}"
    }

    def modulesJson = ['discovery': modules]
    String modulesList = (JsonOutput.toJson(modulesJson))

    logger.info(JsonOutput.toJson(modulesList))
    if (!isDiscoveryModulesRegistered(applicationId, modulesList)) {
      logger.info("do")
    }else{
      Map<String, String> headers = [
        'x-okapi-token': getEurekaToken(),
        'Content-Type': 'application/json'
      ]

      modulesJson.discovery.each { modDiscovery ->

        String url = generateKongUrl("/modules/${modDiscovery.id}/discovery")

        String requestBody = JsonOutput.toJson(modDiscovery)
        try {
          def response = restClient.post(url, requestBody, headers).body

          logger.info("Registered module discovery: ${modDiscovery.id}")
        } catch (RequestException e) {
          if (e.statusCode == HttpURLConnection.HTTP_CONFLICT) {
            // Handle conflict error (module already registered)
            logger.info("Module already registered (skipped): ${modDiscovery.id}")
          } else {
            // Handle other exceptions
            logger.error("Error registering module: ${modDiscovery.id}, error: ${e.message}")
          }
        }
      }
    }
  }
}


//      try {
//        body.discovery.each() { modDiscovery ->
//            response = httpRequest(
//              httpMode: 'POST',
//              url: "${appUrl}/modules/${modDiscovery.id}/discovery",
//
//              requestBody: writeJSON(json: modDiscovery, returnText: true, pretty: 2),
//
//          restClient.post(url, requestBody, headers)
//            content = readJSON(text: response.content)
//        }
//        def requestBody = writeJSON(json: descriptorsList, returnText: true, pretty: 2)
//        logger.warning("HERE")
//        restClient.post(url, requestBody, headers)
//        logger.info("Modules discovery registered: ${descriptorsList}")
//      } catch (RequestException e) {
//        throw new RequestException("Application is not registered", e.statusCode)

//    String url = generateUrl("/modules/discovery")
//
//    descriptorsList.each() { service ->
//      if (service['url'] && service['srvcId'] && service['instId']) {
//        try {
//          restClient.post(url, service, headers)
//          logger.info("${service['srvcId']} registered successfully")
//        } catch (RequestException e) {
//          if (e.statusCode == HttpURLConnection.HTTP_NOT_FOUND) {
//            logger.info("${service['srvcId']} is not registered. ${e.getMessage()}")
//            logger.info("Repeat ${service['srvcId']} retry in 3 seconds.")
//            sleep(3000)
//            restClient.post(url, service, headers)
//          } else {
//            throw new RequestException("${service['srvcId']} is not registered. ${e.getMessage()}", e.statusCode)
//          }
//        }
//        logger.info("Info on the newly created discovery table for id ${applicationId}" ${service})))
//      } else {
//        throw new IllegalArgumentException("${service}: One of required field (srvcId, instId or url) are missing")
//      }
//    }
