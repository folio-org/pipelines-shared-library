package org.folio.rest_v2

import org.folio.models.EurekaTenant
import org.folio.utilities.RequestException

class Eureka extends Authorization {

  public EurekaTenant masterTenant
  static String keycloakUrl
  static String clientSecret
  static String masterTenantId = "master"
  static String masterClientId = "folio-backend-admin-client"

  Eureka(Object context, String eurekaDomain, EurekaTenant masterTenant, boolean debug = false, String keycloakUrl, String clientSecret) {
    super(context, eurekaDomain, debug)
    this.masterTenant = masterTenant
    this.keycloakUrl = keycloakUrl
    this.clientSecret = clientSecret
  }

  void createTenant(EurekaTenant tenant) {
    if (isTenantExist(tenant.tenantId)) {
      logger.warning("Tenant ${tenant.tenantId} already exists!")
      return
    }

    String url = generateUrl("/tenants")
    Map<String, String> headers = getMasterHeaders()
    Map body = [
      name: tenant.tenantId,
      description: tenant.tenantDescription
    ]

    logger.info("Creating tenant ${tenant.tenantId}...")

    restClient.post(url, body, headers)

    logger.info("Tenant (${tenant.tenantId}) successfully created")
  }

  boolean isTenantExist(String tenantId) {
    String url = generateUrl("/tenants/${tenantId}")
    Map<String, String> headers = getMasterHeaders()

    try {
      restClient.get(url, headers)
      logger.info("Tenant ${tenantId} exists")
      return true
    } catch (RequestException e) {
      if (e.statusCode == HttpURLConnection.HTTP_NOT_FOUND) {
        logger.info("Tenant ${tenantId} is not exists")
        return false
      } else {
        throw new RequestException("Can not able to check tenant ${tenantId} existence: ${e.getMessage()}", e.statusCode)
      }
    }
  }

  def getMasterHeaders() {
    return getHttpHeaders(masterTenantId, masterClientId)
  }

  def getHttpHeaders(String tenantId, String clientId) {
    def eurekaToken = getEurekaToken(tenantId, clientId)
    if (tenantId == masterTenantId) {
      return getOkapiHeaders(null, null)
    } else {
      return getOkapiHeaders(tenantId, eurekaToken)
    }
  }

  String getEurekaToken(String tenantId, String clientId) {
    logger.info("Getting access token from Keycloak service")

    String url = "${keycloakUrl}/realms/${tenantId}/protocol/openid-connect/token"
    Map<String,String> headers = ["Content-Type": "application/json"]
    Map body = [
      client_id     : "${clientId}",
      grant_type    : "client_credentials",
      client_secret : "${clientSecret}"
    ]

    def response = restClient.post(url, body, headers).body
    logger.info("Access token received successfully from Keycloak service")

    return response.access_token
  }

  static Map<String,String> getOkapiHeaders(String tenantId, String token) {
    Map<String,String> headers = [:]
    if (tenantId != null && !tenantId.isEmpty()) {
      headers.putAll(['x-okapi-tenant': tenantId])
    }
    if (token != null && !token.isEmpty() && token != "Could not get x-okapi-token") {
      headers.putAll(["x-okapi-token": token])
    }
    return headers
  }
}
