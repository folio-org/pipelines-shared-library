package org.folio.rest_v2

import org.folio.models.EurekaTenant
import org.folio.models.FolioModule
import org.folio.utilities.RequestException
import com.cloudbees.groovy.cps.NonCPS

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
   * Constructor that sets the tenantId and initializes modules.
   * @param context Object that represents the context of the application.
   * @param eurekaDomain Eureka service URL.
   * @param debug Debug flag indicating whether debugging is enabled.
   * @param masterTenant Master Tenant configuration for Eureka.
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

  void createTenant(EurekaTenant tenant) {
    // Get Authorization Headers for Master Tenant from Keycloak
    Map<String, String> headers = getHttpHeaders(masterTenant)

    Map body = [
      name: tenant.tenantId,
      description: tenant.tenantDescription
    ]

    logger.info("Creating tenant ${tenant.tenantId}...")

    // Run POST request to create a new tenant
    restClient.post(this.tenantManagerUrl, body, headers)

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
    def tenantToken = getAuthToken(this.keycloakUrl, tenant.tenantId, tenant.clientId, tenant.clientSecret)
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

/**
 * Get list of Tenants on Folio Application
 * @return List of Tenants` Short Names (IDs)
 */
  List<String> getTenantsList() {
    logger.info("Getting list of Tenants on Application...")

    // Get Authorization Headers for Master Tenant from Keycloak
    Map<String, String> headers = getHttpHeaders(masterTenant)

    // Run POST request to create a new tenant
    def response = restClient.get(this.tenantManagerUrl, headers).body

    logger.info("We've successfully got an Application Tenants List.")

    return response*.name
  }

  /**
   * Register New Application Descriptor
   * @param appDescriptor Application Descriptor as Map
   */
  @NonCPS
  void registerApplication(Map appDescriptor) {
    // Get Authorization Headers for Master Tenant from Keycloak
    Map<String, String> headers = getHttpHeaders(masterTenant)

    String pathParams = "check=false" // Check existence and recreate data scheme if false
    String url = "${this.kongUrl}/applications?${pathParams}"  // URL for POST request

    logger.info("Performing registration for new Application Descriptor...")
    restClient.post(url, appDescriptor, headers) // Run POST request to register New Application Descriptor
    logger.info("New Application Descriptor is registered.")
  }

  /**
   * Create New Module Discovery for Application
   * @param module FolioModule object to be updated
   */
  @NonCPS
  void createModuleDiscovery(FolioModule module) {
    // Get Authorization Headers for Master Tenant from Keycloak
    Map<String, String> headers = getHttpHeaders(masterTenant)

    // URL for POST request
    String url = "${this.kongUrl}/modules/${module.name}-${module.version}/discovery"

    // Request Body for POST request
    Map requestBody = [
      'location': "http://${module.name}:8082",
      'id': "${module.name}-${module.version}",
      'name': module.name,
      'version': module.version
    ]

    logger.info("Performing Module Discovery for for new module version...")

    def response = restClient.post(url, requestBody, headers).body

    logger.info("New Module Discovery is created for ${module.name}-${module.version}.")
  }

  /**
   * Upgrade Folio Application for Tenant
   * @param applicationId Application ID (e.g. app-platform-complete-1.0.0-SNAPSHOT.54)
   * @param tenantShortName Tenant Short Name (e.g. diku)
   */
  @NonCPS
  void upgradeTenantApplication(String applicationId, String tenantShortName) {
    // Get Authorization Headers for Master Tenant from Keycloak
    Map<String, String> headers = getHttpHeaders(masterTenant)

    // Get Tenant UUID by Tenant Short Name
    String tenantUUID = getTenantByName(tenantShortName).id

//    String pathParams="?"

    // URL for PUT request
    String url = "${this.kongUrl}/entitlements"

    // Request Body for PUT request
    Map requestBody = [
      'tenantId': tenantUUID,
      'applications': [ applicationId ]
    ]

    logger.info("Performing Application ${applicationId} Upgrade for Tenant ${tenantShortName}...")

    def response = restClient.put(url, requestBody, headers).body

    logger.info("We've successfully upgraded Application ${applicationId} for Tenant ${tenantShortName}.")
  }

  /** Get Current Application ID from Folio Instance
   * @param appIdPattern Application ID Pattern (e.g. app-platform-complete-*-SNAPSHOT.*)
   * @param tenantShortName Tenant Short Name (e.g. diku)
   * @return Application Descriptor as a HashMap
   */
  HashMap getAppEntitlements(String appIdPattern, String tenantShortName) {
    // Get Authorization Headers for Master Tenant from Keycloak
    Map<String, String> headers = getHttpHeaders(this.masterTenant)

    // Get Tenant UUID by Tenant Short Name
    String tenantUUID = getTenantByName(tenantShortName).id

    // ?query=tenantId=="44f0a9af-4395-44b4-80a8-51cf03f8c897" AND applicationId=="app-platform-complete-*-SNAPSHOT.*"
    String jql = URLEncoder.encode("applicationId==${appIdPattern} AND tenantId==${tenantUUID}", "UTF-8")
    String pathParams="query=${jql}" // Query for Application ID Pattern and Tenant UUID

    String url = "${this.kongUrl}/entitlements?${pathParams}"  // URL for GET request

    this.logger.info("Getting Current Application Descriptor...")

    def response = this.restClient.get(url, headers).body

    this.logger.info("We've successfully got the Current Application Descriptor.")

    return new HashMap(response as Map)
  }

  /**
   * Get Tenant Info by Tenant Short Name
   * @param tenantShortName Tenant Short Name (e.g. diku)
   * @return Tenant Info as a Map
   */
  Map getTenantByName(String tenantShortName) {
    // Get Authorization Headers for Master Tenant from Keycloak
    Map<String, String> headers = getHttpHeaders(masterTenant)

    // https://{{kong_fqdn}}/tenants?query=name==diku
    // method=GET, url=https://folio-eureka-volya-kong.ci.folio.org/tenants?query=name=="diku"
    String pathParams = "query=name==${tenantShortName}" // Query for Tenant Short Name
    String url = "${this.tenantManagerUrl}?${pathParams}"  // URL for GET request

    logger.info("Getting Tenant Info by Tenant Short Name...")

    def response = restClient.get(url, headers).body

    logger.info("We've successfully got the Tenant Info.")

    return response?.tenants[0] as Map
  }

  /**
   * Get Application Descriptor by its ID
   * @param appId Application ID
   * @return Application Descriptor as a Map
   */
  @NonCPS
  Map getApplicationDescriptor(String appId) {
    // Get Authorization Headers for Master Tenant from Keycloak
    Map<String, String> headers = getHttpHeaders(masterTenant)

    String url = "${this.kongUrl}/applications/${appId}"  // URL for GET request

    logger.info("Getting Application Descriptor by its ID...")

    def response = restClient.get(url, headers).body

    logger.info("We've successfully got the Application Descriptor.")

    return response as Map
  }

  /**
   * Get Updated Application Descriptor with new Module Version
   * @param appDescriptor Current Application Descriptor as a Map
   * @param module Module object to be updated
   * @param buildNumber Build Number for new Application Version
   * @return Updated Application Descriptor as a Map
   */
  @NonCPS
  Map getUpdatedApplicationDescriptor(Map appDescriptor, FolioModule module, String buildNumber) {
    // Update Application Descriptor with new Application Version
    String currentAppVersion = appDescriptor.version
    String newAppVersion = currentAppVersion.replaceFirst(/SNAPSHOT\.\d+/, "SNAPSHOT.${buildNumber}")
    appDescriptor.version = newAppVersion
    appDescriptor.id = "${appDescriptor.name}-${newAppVersion}"

    // Update Application Descriptor with new Module Version
    appDescriptor.modules.findAll { it.name == module.name }.each {
      it.url = "${org.folio.Constants.EUREKA_REGISTRY_URL}${module.name}-${module.version}"
      it.id = "${module.name}-${module.version}"
      it.version = module.version
    }

    logger.info("Updated Application Descriptor with new Module Version: ${module.name}-${module.version}")

    return appDescriptor as Map
  }
}
