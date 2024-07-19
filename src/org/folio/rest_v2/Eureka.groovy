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
//    String url = generateKongUrl("/applications?check=false")
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

    def jsonSlurper = new JsonSlurperClassic()
    def parsedJson = jsonSlurper.parseText(descriptorsList)
    def modules = parsedJson.modules

    modules.each { module ->
      module.location = "http://${module.name}:8082"
    }

    def modulesJson = ['discovery': modules]
    String modulesList = (JsonOutput.toJson(modulesJson))
    String modulesToRegister = (JsonOutput.toJson(modules))


    def result = isDiscoveryModulesRegistered(applicationId, modulesList)
    logger.warning("${modulesJson}")
    logger.warning("${modulesList}")
    logger.warning("${modulesToRegister}")
    if (result == false) {
      logger.info("All modules are already registered. No further action needed.")
    } else if (result == null) {
      Map<String, String> headers = [
        'x-okapi-token': getEurekaToken(),
        'Content-Type' : 'application/json'
      ]
      try {
        String url = generateKongUrl("/modules/discovery")
        logger.info("Going to register modules")
        restClient.post(url, modulesToRegister, headers).body

      } catch (RequestException e) {
        if (e.statusCode == HttpURLConnection.HTTP_CONFLICT) {
          logger.warning("Some modules already register modules. Proceed with registration one by one")
        } else {
          logger.info("here")
          throw new RequestException("Error registering module ${e.statusCode}")
        }
      }
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


//modulesJson.discovery.each { modDiscovery ->
//
//  String url = generateKongUrl("/modules/${modDiscovery.id}/discovery")
//  String requestBody = JsonOutput.toJson(modDiscovery)
//
//  try {
//    String url = generateKongUrl("/modules/${modDiscovery.id}/discovery")
//    restClient.post(url, requestBody, headers).body
//    logger.info("Registered module discovery: ${modDiscovery.id}")
//  } catch (RequestException e) {
//    if (e.statusCode == HttpURLConnection.HTTP_CONFLICT) {
//      logger.info("Module already registered (skipped): ${modDiscovery.id}")
//    } else {
//      throw new RequestException("Error registering module: ${modDiscovery.id}, error: ${e.statusCode}")
//    }
//  }
//}
//} else {
//  logger.info("Not all of the module discovery information is registered. Going to register one by one.")
//  String url = generateKongUrl("/modules/discovery")
//  def registeredDiscoveryId = content.discovery.collect() { it.id }
//  body.discovery.each() { modDiscovery ->
//    if (!registeredDiscoveryId.contains(modDiscovery.id)) {
//      restClient.post(url, requestBody, headers).body
//
//      logger.info("Info on the newly created discovery table for id ${applicationId}\n Status: ${response.status}\n Response content:")
//      logger.info("${content}")
//    }
//  }
//}
//}
