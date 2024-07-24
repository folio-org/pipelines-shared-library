package org.folio.rest_v2

import groovy.json.JsonOutput
import groovy.json.JsonSlurperClassic
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

  def isDiscoveryModulesRegistered(String applicationId, String modulesJson) {

    String url = generateKongUrl("/applications/${applicationId}/discovery?limit=500")
    def jsonSlurper = new JsonSlurperClassic()
    def modulesMap = jsonSlurper.parseText(modulesJson)

    def response = restClient.get(url)
    def content = response.body

    if (content.totalRecords == modulesMap.discovery.size()) {
      logger.info("All module discovery information are registered. Nothing to do.")
      return false
    } else if (content.totalRecords == 0) {
      logger.info("Any discovery modules is registerd. Proceeding with registration.")
      return null
    } else {
      logger.info("Not all modules discovery is registered. Proceeding with registration.")
      return content
    }
  }

  void registerApplicationDiscovery(String applicationId) {
    String descriptorsList = getDescriptorsList(applicationId)

    def jsonSlurper = new JsonSlurperClassic()
    def parsedJson = jsonSlurper.parseText(descriptorsList)
    def modules = parsedJson.modules

    modules.each { module ->
      module.location = "http://${module.name}:8082"
    }

    def modulesJson = ['discovery': modules]

    String modulesList = (JsonOutput.toJson(modulesJson))

    def result = isDiscoveryModulesRegistered(applicationId, modulesList)

    if (result == false) {
      logger.info("All modules are already registered. No further action needed.")
    } else if (result == null) {
      Map<String, String> headers = [
        'x-okapi-token': getEurekaToken(),
        'Content-Type' : 'application/json'
      ]
      String url = generateKongUrl("/modules/discovery")
      logger.info("Going to register modules\n ${modulesJson}")
      restClient.post(url, modulesList, headers).body

    } else {

      Map<String, String> headers = [
        'x-okapi-token': getEurekaToken(),
        'Content-Type' : 'application/json'
      ]
      modulesJson.discovery.each { modDiscovery ->

        String requestBody = JsonOutput.toJson(modDiscovery)

        try {
          String url = generateKongUrl("/modules/${modDiscovery.id}/discovery")
          restClient.post(url, requestBody, headers).body
          logger.info("Registered module discovery: ${modDiscovery.id}")
        } catch (RequestException e) {
          if (e.statusCode == HttpURLConnection.HTTP_CONFLICT) {
            logger.info("Module already registered (skipped): ${modDiscovery.id}")
          } else {
            throw new RequestException("Error registering module: ${modDiscovery.id}, error: ${e.statusCode}")
          }
        }
      }
    }
  }
}
