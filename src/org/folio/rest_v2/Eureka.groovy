package org.folio.rest_v2

import org.folio.models.OkapiTenant

class Eureka extends Authorization {

  public OkapiTenant superTenant

  Eureka(Object context, String okapiDomain, OkapiTenant superTenant, boolean debug = false) {
    super(context, okapiDomain, debug)
    this.superTenant = superTenant
  }


  void registerApplication(List descriptorsList) {
    String url = generateUrl("/applications?check=false")
    Map<String, String> headers = getAuthorizedHeaders(superTenant)
// To do: work with headers. tenant id and token from new method getEurikaToken
    //Check application descriptor .Define type list or map
    restClient.post(url, descriptorsList, headers)
    logger.info("Application registered: ${descriptorsList.size()}")
  }

  void registerApplicationDiscovery(applicationId) {
    String url = generateUrl("/applications/${applicationId}/discovery?limit=500")
    Map<String, String> headers = getAuthorizedHeaders(superTenant)

//To do: get application and then post
    restClient.get(url)
    logger.info("Application discovery registered: ${url}")
  }
}

