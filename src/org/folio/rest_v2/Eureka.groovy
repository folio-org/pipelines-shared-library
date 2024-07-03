package org.folio.rest_v2

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

  void registerApplicationDiscovery(List descriptorsList, String applicationId, OkapiTenant tenant) {

    if (isDiscoveryRegistered(tenant, applicationId, descriptorsList)) {
      logger.warning("All module discovery information are registered. Nothing to do.")
      return
    }

    String url = generateUrl("/modules/discovery")
    Map<String, String> headers = getAuthorizedHeaders(tenant)

    restClient.post(registerUrl, descriptorsList, headers)
    logger.info("Application discovery registered: ${url}")
  }
}
