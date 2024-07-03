package org.folio.rest_v2

import org.folio.models.OkapiTenant
import org.folio.models.RancherNamespace
import org.folio.utilities.RequestException

class Eureka extends Authorization {

  public OkapiTenant superTenant

  Eureka(Object context, String okapiDomain, OkapiTenant superTenant, boolean debug = false) {
    super(context, okapiDomain, debug)
    this.superTenant = superTenant
  }

  def createTenant(OkapiTenant tenant) {
    String url = generateUrl("/tenants")
    Map<String, String> headers = getMasterRealmHeaders(superTenant)
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
    Map<String, String> headers = getMasterRealmHeaders(superTenant)

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

  def getMasterRealmHeaders(RancherNamespace ns, OkapiTenant tenant, String awsRegion) {
    return getHttpHeaders(ns, 'master', 'folio-backend-admin-client', awsRegion)
  }

  def getHttpHeaders(RancherNamespace ns, String tenantId, String clientId, String awsRegion) {
    def keycloakUrl = getKeycloakUrl(ns)
    def clientSecret = getClientSecret(tenantId, clientId, awsRegion)
    def eurekaToken = getEurekaToken(keycloakUrl, tenantId, clientId, clientSecret)
    return getOkapiHeaders(tenantId == 'master' ? '' : tenantId, eurekaToken)
  }

  def getKeycloakUrl(RancherNamespace ns) {
    def keycloakUrl = "https://${ns.generateDomain('keycloak')}"
    logger.info("Keycloak URL: ${keycloakUrl}")
    return keycloakUrl
  }

  def getClientSecret(String tenantId, String clientId, String awsRegion) {
    def awsParameterName = "folio_${tenantId}_${clientId}"
    try {
      return awscli.getSsmParameterValue(awsRegion, awsParameterName)
    } catch (Exception e) {
      logger.error("Error fetching '${awsParameterName}' parameter value from AWS SSM: ${e.message}")
    }
  }

  def getEurekaToken(String keycloakUrl, String tenantId, String clientId, String clientSecret) {
    logger.info("Getting access token from Keycloak service")
    def url = "${keycloakUrl}/realms/${tenantId}/protocol/openid-connect/token"
    def headers = ["Content-Type": "application/json"]
    def body = "client_id=${clientId}&grant_type=client_credentials&client_secret=${clientSecret}"
    def response = restClient.post(url, body, headers)
    def content = readJSON(text: response.content)
    logger.info("Access token received successfully from Keycloak service")
    return content.access_token
  }

  def getOkapiHeaders(String tenantId, String token) {
    def headers = []
    if (tenantId != null && !tenantId.isEmpty()) {
      // JWT contains information about the tenant and this header could be omitted
      // not in all places where the headers map is generated the tenantId passed to the function
      headers.add([name: 'x-okapi-tenant', value: tenantId])
    }
    if (token != null && !token.isEmpty() && !token.equals("Could not get x-okapi-token")) {
      headers.add([name:"x-okapi-token", value: token, maskValue: true])
    }
    return headers
  }
}
