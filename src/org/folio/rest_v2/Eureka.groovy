package org.folio.rest_v2

import org.folio.models.OkapiTenant
import org.folio.utilities.RequestException

class Eureka extends Authorization {

  public OkapiTenant superTenant

  Eureka(Object context, String okapiDomain, boolean debug = false) {
    super(context, okapiDomain, debug)
  }

  boolean isApplicationRegistered(String applicationId) {

    String url = generateKongUrl("/applications/${applicationId}")

    try {
      restClient.get(url, headers).body
      logger.info("Application ${applicationId} is already registered.")
      return true
    } catch (RequestException e) {
      if (e.statusCode == HttpURLConnection.HTTP_NOT_FOUND) {
        logger.info("Application id ${applicationId} not found in Application manager. Proceeding with registration.")
        return false
      } else {
        throw new RequestException("Application manager is unavailable", e.statusCode)
      }
    }
  }

//  boolean isDiscoveryRegistered(OkapiTenant tenant, String applicationId, List descriptorsList) {
//
//    String url = generateUrl("/applications/${applicationId}/discovery?limit=500")
//    Map<String, String> headers = getAuthorizedHeaders(tenant)
//
//    def response = restClient.get(url, headers)
//    def content = response.body
//
//    if (isDiscoveryRegistered.content == descriptorsList) {
//      logger.info("All module discovery information are registered. Nothing to do.")
//      return true
//    } else {
//      logger.info("Not all module discovery information is registered. Proceeding with registration.")
//      return false
//    }
//  }

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

  def registerApplication(String applicationId) {
    String descriptorsList = getDescriptorsList(applicationId)
    if (isApplicationRegistered(applicationId)) {
      logger.warning("Application ${applicationId} is already registered.")
      return
    }

    String url = generateKongUrl("/applications?check=false")
    Map<String, String> headers = getAuthorizedHeaders(superTenant)

    restClient.post(url, descriptorsList, headers)
    logger.info("Application registered: ${descriptorsList}")
  }

//  void registerApplicationDiscovery(String applicationId, OkapiTenant tenant) {
//    String descriptorsList = GetDescriptotsList(applicationId)
//    if (isDiscoveryRegistered(tenant, applicationId, descriptorsList)) {
//      logger.warning("All module discovery information are registered. Nothing to do.")
//      return
//    } else (isDiscoveryModulesRegistered(tenant, descriptorsList)) {
//      logger.info("Application discovery registered")
//      return
//    }
//
//    String url = generateUrl("/modules/discovery")
//    Map<String, String> headers = getAuthorizedHeaders(tenant)
//// add port
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
//        logger.info("Info on the newly created discovery table for id ${applicationId}" JsonOutput.prettyPrint(JsonOutput.toJson(response)))
//      } else {
//        throw new IllegalArgumentException("${service}: One of required field (srvcId, instId or url) are missing")
//      }
//    }
//  }

  def getDescriptorsList(applicationId) {

    String bucketName = 'eureka-application-registry'
    steps.awscli.withAwsClient(){
      steps.sh(script: "aws s3api get-object --bucket ${bucketName} --key ${applicationId} ${applicationId}.json")
    }
    logger.warning(readJSON(file: "${applicationId}.json"))
    return readJSON(file: "${applicationId}.json")
  }
}


//https://folio-eureka-scout-kong.ci.folio.org/
