package org.folio.rest_v2

import org.folio.models.OkapiTenant
import org.folio.utilities.RequestException

class Eureka extends Authorization {

  public OkapiTenant superTenant

  Eureka(Object context, String okapiDomain, OkapiTenant superTenant, boolean debug = false) {
    super(context, okapiDomain, debug)
  }

  boolean isApplicationRegistered(String applicationId) {

    String checkUrl = generateUrl("/applications/${applicationId}")
    try {
      def response = restClient.get(checkUrl, headers).body
      int statusCode = response.status
    } catch (RequestException e) {
      if (statusCode == 200) {
        def content = response.body
        logger.info("Application already registered: ${content}")
        return true

      } else if (statusCode == 503) {
        throw new RequestException("Application manager is unavailable", e.statusCode)

      } else {
        logger.info("Application id ${applicationId} not found in Application manager. Proceeding with registration.")
        return false

      }
    }
  }

  boolean isDiscoveryRegistered(OkapiTenant tenant, String applicationId) {

    String discoveryUrl = generateUrl("/applications/${applicationId}/discovery?limit=500")
    Map<String, String> headers = getAuthorizedHeaders(tenant)

    def response = restClient.get(discoveryUrl, headers)
    def content = response.body

    if (isDiscoveryRegistered.content == descriptorsList) {
      logger.info("All module discovery information are registered. Nothing to do.")
      return true
    } else {
      logger.info("Not all module discovery information is registered. Proceeding with registration.")
      return false
    }
  }

  void registerApplication(List descriptorsList, OkapiTenant tenant, String applicationId) {

    if (!isApplicationRegistered(applicationId)) {

      String url = generateUrl("/applications?check=false")
      Map<String, String> headers = getAuthorizedHeaders(tenant)

      restClient.post(url, descriptorsList, headers)
      logger.info("Application registered: ${descriptorsList}")
    } else {
      logger.info("Application ${applicationId} is already registered.")
    }
  }

  void registerApplicationDiscovery(List descriptorsList, String applicationId, OkapiTenant tenant) {

    if (!isDiscoveryRegistered(tenant, applicationId)) {

      String registerUrl = generateUrl("/modules/discovery")

      restClient.post(registerUrl, descriptorsList, headers)
      logger.info("Application discovery registered: ${registerUrl}")

    } else {
      logger.info("All module discovery information are registered. Nothing to do.")
    }
  }
}
