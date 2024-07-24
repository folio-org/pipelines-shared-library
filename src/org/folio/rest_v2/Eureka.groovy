package org.folio.rest_v2

import org.folio.models.EurekaTenant
import org.folio.utilities.RequestException

class Eureka extends Common {

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

  boolean isApplicationRegistered(String applicationId) {

    String url = generateKongUrl("/applications/${applicationId}")

    try {
      restClient.get(url).body
      logger.info("Application ${applicationId} is already registered.")
      return true
    } catch (RequestException e) {
      if (e.statusCode == HttpURLConnection.HTTP_NOT_FOUND) {
        logger.info("Application id ${applicationId} not found in Application manager. Proceeding with registration.")
        return false
      } else {
        throw new RequestException("Application manager is unavailable", e.statusCode)
      }
    }
  }

  def getDescriptorsList(applicationId) {

    steps.awscli.withAwsClient() {
      steps.sh(script: "aws s3api get-object --bucket ${Constants.EUREKA_BUCKET_NAME} --key apps/${applicationId}.json ${applicationId}.json")
    }
    logger.info(steps.readJSON(file: "${applicationId}.json"))
    return steps.readJSON(file: "${applicationId}.json")
  }

  def registerApplication(String applicationId) {
    String descriptorsList = getDescriptorsList(applicationId)
    if (isApplicationRegistered(applicationId)) {
      logger.warning("Application ${applicationId} is already registered.")
      return
    }

    String url = "${Constants.EUREKA_KONG_URL}applications?check=false"
    Map<String,String> headers = [
      'x-okapi-token': getEurekaToken(),
      'Content-Type': 'application/json'
    ]
    try {
      restClient.post(url, descriptorsList, headers)
      logger.info("Application registered: ${descriptorsList}")
    } catch (RequestException e) {
      throw new RequestException("Application is not registered", e.statusCode)
    }
  }
}
