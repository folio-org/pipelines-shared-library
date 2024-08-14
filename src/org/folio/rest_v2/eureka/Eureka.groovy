package org.folio.rest_v2.eureka

import hudson.util.Secret
import org.folio.models.EurekaTenant
import org.folio.models.OkapiTenant
import org.folio.models.OkapiUser
import org.folio.rest_v2.Common
import org.folio.utilities.RequestException

class Eureka extends Common {

  /**
   * EurekaTenant object contains Master Tenant configuration for Eureka.
   * @attribute 'tenantId'     Default: "master"
   * @attribute 'clientId'     Default: "folio-backend-admin-client"
   */
  public EurekaTenant masterTenant

  /**
   * Keycloak service URL.
   * Is the same for all Tenants
   */
  String keycloakUrl

  /**
   * Kong service URL.
   * Is the same for all Tenants
   */
  String kongUrl

  /**
   * Tenant Manager Service URL.
   * Is the same for all Tenants.
   */
  String tenantManagerUrl

  /**
   * Eureka Class Constructor sets required parameters for REST HTTP Request.
   * @param context Jenkins context.
   * @param eurekaDomain Eureka domain.
   * @param debug Debug mode.
   * @param masterTenant Master Tenant configuration.
   * @param keycloakUrl Keycloak service URL.
   * @param kongUrl Kong service URL.
   */
  Eureka( Object context, String eurekaDomain, boolean debug = false,
    EurekaTenant masterTenant,
    String keycloakUrl,
    String kongUrl
  ) {
    super(context, eurekaDomain, debug)
    this.masterTenant = masterTenant
    this.keycloakUrl = keycloakUrl
    this.kongUrl = kongUrl
    this.tenantManagerUrl = "${kongUrl}/tenants"
  }

  Eureka(Object context, String kongUrl, String keycloakUrl, boolean debug = false) {
    super(context, kongUrl, debug)
  }

  void initializeFromScratch(Map<String, OkapiTenant> tenants, boolean enableConsortia) {
    //TODO
  }

  void createTenant(EurekaTenant tenant) {
    // Get Authorization Headers for Master Tenant from Keycloak
    Map<String, String> headers = getHttpHeaders(masterTenant)

    Map body = [
      name: tenant.tenantId,
      description: tenant.tenantDescription
    ]

    logger.info("Creating tenant ${tenant.tenantId}...")

    // Run POST request to create a new tenant
    restClient.post(tenant.tenantManagerUrl, body, headers)

    logger.info("Tenant (${tenant.tenantId}) successfully created")
  }

  boolean isTenantExist(String endpointUrl, String tenantId, Map<String, String> httpHeaders) {
    String tenantUrl = "${endpointUrl}/${tenantId}"

    try {
      restClient.get(tenantUrl, httpHeaders)
      logger.info("Tenant ${tenantId} exists")
      return true
    } catch (RequestException e) {
        logger.warning("statusCode: ${e.statusCode}: Not able to check tenant ${tenantId} existence: ${e.getMessage()}")
        return false
    }
  }

  def getHttpHeaders(EurekaTenant tenant) {
    def tenantId = (tenant.tenantId == masterTenant.tenantId) ? "" : tenant.tenantId
    def tenantToken = getAuthToken(tenant.keycloakUrl, tenant.tenantId, tenant.clientId, tenant.clientSecret)
    return getAuthHeaders(tenantId, tenantToken)
  }

  String getAuthToken(String keycloakUrl, String tenantId, String clientId, String clientSecret) {
    logger.info("Getting access token from Keycloak service")

    String url = "${keycloakUrl}/realms/${tenantId}/protocol/openid-connect/token"
    Map<String,String> headers = ['Content-Type':'application/x-www-form-urlencoded']
    String requestBody = "client_id=${clientId}&client_secret=${clientSecret}&grant_type=client_credentials"

    def response = restClient.post(url, requestBody, headers).body
    logger.info("Access token received successfully from Keycloak service")

    return response['access_token']
  }

  Map<String,String> getAuthHeaders(String tenantId, String token) {
    Map<String,String> headers = [:]

    if (tenantId != null && !tenantId.isEmpty()) {
      headers.putAll(['x-okapi-tenant': tenantId])
    }

    if (token != null && !token.isEmpty()) {
      headers.putAll(['Authorization' : "Bearer ${token}"])
    }

    if (!headers.isEmpty()) {
      logger.info("Auth HTTP Headers are populated")
    }

    return headers
  }
}
