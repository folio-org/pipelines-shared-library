package org.folio.rest_v2

import groovy.json.JsonOutput
import org.folio.models.OkapiTenant
import org.folio.utilities.RequestException

class Eureka extends Authorization {

  public OkapiTenant superTenant

  Eureka(Object context, String okapiDomain, OkapiTenant superTenant, boolean debug = false) {
    super(context, okapiDomain, debug)
  }

  boolean isApplicationRegistered(String applicationId) {

    String url = generateUrl("/applications/${applicationId}")

    try {
      restClient.get(url, headers).body
      logger.info("Application ${applicationId} is already registered.")
      return true
      } catch (RequestException e) {
      if (e.statusCode == HttpURLConnection.HTTP_NOT_FOUND){
        logger.info("Application id ${applicationId} not found in Application manager. Proceeding with registration.")
        return false
      } else {
        throw new RequestException("Application manager is unavailable", e.statusCode)
      }
    }
  }

  boolean isDiscoveryRegistered(OkapiTenant tenant, String applicationId, List descriptorsList) {

    String url = generateUrl("/applications/${applicationId}/discovery?limit=500")
    Map<String, String> headers = getAuthorizedHeaders(tenant)

      def response = restClient.get(url, headers)
      def content = response.body

      if (isDiscoveryRegistered.content == descriptorsList) {
        logger.info("All module discovery information are registered. Nothing to do.")
        return true
      } else {
        logger.info("Not all module discovery information is registered. Proceeding with registration.")
        return false
      }
  }

  boolean isDiscoveryModulesRegistered(List descriptorsList, OkapiTenant tenant) {

    String url = generateUrl("/modules/discovery")
    Map<String, String> headers = getAuthorizedHeaders(tenant)

    try {
      restClient.post(url, descriptorsList, headers)
      logger.info("Application discovery registered: ${url}")
      return true
    } catch (RequestException e) {
      if (e.statusCode ==! HttpURLConnection.HTTP_CREATED){
        logger.info("Not all of the module discovery information is registered. Going to register one by one")
        return false
      } else {
        throw new RequestException("Discovery modules is unavailable", e.statusCode)
      }
    }
  }

  void registerApplication(List descriptorsList, OkapiTenant tenant, String applicationId) {

    if (isApplicationRegistered(applicationId)) {
      logger.warning("Application ${applicationId} is already registered.")
      return
    }

    String url = generateUrl("/applications?check=false")
    Map<String, String> headers = getAuthorizedHeaders(tenant)

    restClient.post(url, descriptorsList, headers)
    logger.info("Application registered: ${descriptorsList}")
  }

  void registerApplicationDiscovery(List descriptorsList, String applicationId, OkapiTenant tenant, String region) {

    if (isDiscoveryRegistered(tenant, applicationId, descriptorsList)) {
      logger.warning("All module discovery information are registered. Nothing to do.")
      return
    } else (isDiscoveryModulesRegistered(tenant, descriptorsList)) {
      logger.info("Application discovery registered")
      return
    }

    def body = ['discovery':[]]
    descriptorsList.modules.each() { module ->
      def moduleUrl = "http://${module.name}-b.${folio}.folio-eis.${region}:8051/${module.name}".toString()
      module.put('location', moduleUrl)
      body.discovery.add(module)
    }
    body.discovery.each() { modDiscovery ->
      String url = generateUrl("/modules/${modDiscovery.id}/discovery")
      Map<String, String> headers = getAuthorizedHeaders(tenant)
      def response = restClient.post(url, modDiscovery, headers)
    }

    logger.info("Info on the newly created discovery table for id ${applicationId}\\n" JsonOutput.prettyPrint(JsonOutput.toJson(response)))
  }
}

