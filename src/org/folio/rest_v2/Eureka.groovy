package org.folio.rest_v2

import org.folio.models.EurekaTenant
import org.folio.utilities.RequestException

class Eureka extends Authorization {

  /**
   * EurekaTenant object contains Master Tenant configuration for Eureka.
   * @attribute 'tenantId'     Default: "master"
   * @attribute 'clientId'     Default: "folio-backend-admin-client"
   */
  public EurekaTenant masterTenant

  Eureka(Object context, String eurekaDomain, EurekaTenant masterTenant, boolean debug = false) {
    super(context, eurekaDomain, debug)
    this.masterTenant = masterTenant
  }

  void createTenant(EurekaTenant tenant) {
    if (isTenantExist(tenant.tenantManagerUrl, tenant.tenantId)) {
      logger.warning("Tenant ${tenant.tenantId} already exists!")
      return
    }

    Map<String, String> headers = getMasterHeaders()
    Map body = [
      name: tenant.tenantId,
      description: tenant.tenantDescription
    ]

    logger.info("Creating tenant ${tenant.tenantId}...")

    restClient.post(tenant.tenantManagerUrl, body, headers)

    logger.info("Tenant (${tenant.tenantId}) successfully created")
  }

  boolean isTenantExist(String endpointUrl, String tenantId) {
    String tenantUrl = "${endpointUrl}/${tenantId}"
    Map<String, String> headers = getMasterHeaders()

    try {
      restClient.get(tenantUrl, headers)
      logger.info("Tenant ${tenantId} exists")
      return true
    } catch (RequestException e) {
        logger.warning("statusCode: ${e.statusCode}: Not able to check tenant ${tenantId} existence: ${e.getMessage()}")
        return false
    }
  }

  def getMasterHeaders() {
    return getHttpHeaders(masterTenant)
  }

  def getHttpHeaders(EurekaTenant tenant) {
    def tenantId = (tenant.tenantId == masterTenant.tenantId) ? "" : tenant.tenantId
    def eurekaToken = getEurekaToken(tenant.keycloakUrl, tenant.tenantId, tenant.clientId, tenant.clientSecret)
    return getOkapiHeaders(tenantId, eurekaToken)
  }

  String getEurekaToken(String keycloakUrl, String tenantId, String clientId, String clientSecret) {
    logger.info("Getting access token from Keycloak service")

    String url = "${keycloakUrl}/realms/${tenantId}/protocol/openid-connect/token"
    Map<String,String> headers = ['Content-Type':'application/x-www-form-urlencoded']
    String requestBody = "client_id=${clientId}&client_secret=${clientSecret}&grant_type=client_credentials"

    def response = restClient.post(url, requestBody, headers).body
    logger.info("Access token received successfully from Keycloak service")

    return response.access_token
  }

  Map<String,String> getOkapiHeaders(String tenantId, String token) {
    Map<String,String> headers = [:]
    if (tenantId != null && !tenantId.isEmpty()) {
      headers.putAll(['x-okapi-tenant': tenantId])
    }
    if (token != null && !token.isEmpty() && token != "Could not get x-okapi-token") {
      headers.putAll(["x-okapi-token": token])
    }
    if (!headers.isEmpty()) {
      logger.info("Important 'X-Okapi' Http Headers are configured")
    }
    return headers
  }
}
