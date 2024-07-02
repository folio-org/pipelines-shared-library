package org.folio.rest_v2

import org.folio.models.OkapiTenant

class Eureka extends Authorization {

  public OkapiTenant superTenant

  Eureka(Object context, String okapiDomain, OkapiTenant superTenant, boolean debug = false) {
    super(context, okapiDomain, debug)
    this.superTenant = superTenant
  }


  
  void registerApplication(List descriptorsList, tenantId) {

    String url = generateUrl("/applications?check=false")
    Map<String, String> headers = getOkapiToken(tenantId)

    def response = restClient.get(url, headers).body

    restClient.post(url, descriptorsList, headers)
    logger.info("Application registered: ${descriptorsList}")
  }

  void registerApplicationDiscovery(List descriptorsList, applicationId, tenantId) {

    String discoveryUrl = generateUrl("/applications/${applicationId}/discovery?limit=500")
    Map<String, String> headers = getOkapiToken(tenantId)

    def response = restClient.get(discoveryUrl, headers)
    def content = response.body

    if (content == descriptorsList) {
      logger.info("All module discovery information are registered. Nothing to do.")
    } else {
      logger.info("Not all module discovery information is registered. Proceeding with registration.")

      String registerUrl = generateUrl("/modules/discovery")

      restClient.post(registerUrl, headers)
      logger.info("Application discovery registered: ${registerUrl}")
    }
  }
}

