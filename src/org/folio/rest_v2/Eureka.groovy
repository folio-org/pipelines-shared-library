package org.folio.rest_v2

import org.folio.models.OkapiTenant

class Eureka extends Authorization {

  public OkapiTenant superTenant

  Eureka(Object context, String okapiDomain, OkapiTenant superTenant, boolean debug = false) {
    super(context, okapiDomain, debug)
    this.superTenant = superTenant
  }


  void registerApplication(List descriptorsList, OkapiTenant tenant, String applicationId) {

    String checkUrl = generateUrl("/applications/${applicationId}")
    def response = restClient.get(checkUrl, headers).body
    int statusCode = response.status

    if (statusCode == 200) {
      def content = response.body
      logger.info("Application already registered: ${content}")

    } else if (statusCode == 503) {
      logger.error("Application manager is unavailable")

    } else {
      logger.info("Application id ${applicationId} not found in Application manager. Proceeding with registration.")

      String url = generateUrl("/applications?check=false")
      Map<String, String> headers = getAuthorizedHeaders(tenant)


      restClient.post(url, descriptorsList, headers)
      logger.info("Application registered: ${descriptorsList}")
    }
  }

  void registerApplicationDiscovery(List descriptorsList, String applicationId, OkapiTenant tenant) {

    String discoveryUrl = generateUrl("/applications/${applicationId}/discovery?limit=500")
    Map<String, String> headers = getAuthorizedHeaders(tenant)

    def response = restClient.get(discoveryUrl, headers)
    def content = response.body

    if (content == descriptorsList) {
      logger.info("All module discovery information are registered. Nothing to do.")
    } else {
      logger.info("Not all module discovery information is registered. Proceeding with registration.")

      String registerUrl = generateUrl("/modules/discovery")

      restClient.post(registerUrl, descriptorsList, headers)
      logger.info("Application discovery registered: ${registerUrl}")
    }
  }
}

