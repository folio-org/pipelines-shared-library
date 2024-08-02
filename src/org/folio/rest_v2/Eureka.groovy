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

  /**
   * Get Data for provided Role Name.
   *
   * @param tenantHeaders HTTP Headers for Tenant.
   * @param roleName Role Name to get data for.
   * @param roleDescription Description for requested Role.
   * @return Created Role Data as a JSON object.
   */
  def createRole(Map<String, String> tenantHeaders, String roleName, String roleDescription='') {
    // Check if role exists
    def existingRole = getRole(tenantHeaders, roleName)
    if (existingRole) {
      return existingRole.id
    } else {
      // Create new role
      logger.info("Creating role ${roleName}...")
      String url = "${masterTenant.kongUrl}/roles"
      Map body = [
        name: roleName,
        description: roleDescription ?: roleName
      ]
      def response = restClient.post(url, body, tenantHeaders).body
      logger.info("Role ${roleName} created successfully")
      return readJSON(text: response)
    }
  }

  /**
   * Get Data for provided Role Name.
   *
   * @param tenantHeaders HTTP Headers for Tenant.
   * @param roleName Role Name to get data for.
   * @return Role Data as a JSON object.
   */
  def getRole(Map<String, String> tenantHeaders, String roleName) {
    logger.info("Checking if role ${roleName} exists...")

    String url = "${masterTenant.kongUrl}/roles?query=name==${roleName}"
    String response = restClient.get(url, tenantHeaders).body
    List<Object> roles = (readJSON text: response).roles

    if (roles) {
      logger.info("Role ${roleName} exists")
      return roles.first()
    } else {
      logger.info("Role ${roleName} does not exist")
      return null
    }
  }

  /**
   * Get Data for requested Capabilities.
   *
   * @param tenantHeaders HTTP Headers for Tenant.
   * @param query Query to pick requested Capabilities. Default is empty.
   * @param limit Limit of data to get. Default is 10000.
   * @return Capabilities as a List of JSON objects.
   */
  List<Object> getCapabilities(Map<String, String> tenantHeaders, String query = '', Integer limit = 10000) {
    logger.info("Getting capabilities...")

    String url = "${masterTenant.kongUrl}/capabilities${query}&limit=${limit}\" : \"${masterTenant.kongUrl}/capabilities?limit=${limit}"
    String response = restClient.get(url, tenantHeaders).body

    logger.info("Capabilities received successfully")

    return readJSON(text: response).capabilities
  }

  /**
   * Assign Capabilities to Role.
   *
   * @param tenantHeaders HTTP Headers for Tenant.
   * @param roleId Uniq Role ID.
   * @param type Capability Type.
   * @param resourceIds List of Capabilities IDs to assign.
   */
  void assignCapabilitiesToRole(Map<String, String> tenantHeaders, String roleId, String type, List resourceIds) {
    logger.info("Assigning capabilities to role ${roleId}...")

    String url
    String body

    if (type == 'capability') {
      url = "${masterTenant.kongUrl}/roles/capabilities"
      body = [
        "roleId": roleId,
        "capabilityIds": resourceIds
      ]
    } else if (type == 'capability-set') {
      url = "${masterTenant.kongUrl}/roles/capability-sets"
      body = [
        "roleId": roleId,
        "capabilitySetIds": resourceIds
      ]
    } else {
      throw new Exception("Unsupported type of assigment")
    }

    logger.debug("Body: ${body}, RoleId: ${roleId}, ResourceIds: ${resourceIds}")

    try {
      String response = restClient.post(url, body, tenantHeaders).body

      if (response.status == 201) {
        logger.info("Relation between role ${roleId} and ${type} ${resourceIds} created!")
      }
      else if (response.status == 400 && response.contains("Relation")) {
        logger.info("Relation between role ${roleId} and ${type} ${resourceIds} already exist!")
      } else {
        throw new Exception("Error happened during assigning ${type} to role. Error: ${response}")
      }
    } catch(ex) {
      logger.error("Not able to assign ${resourceIds} to ${roleId}")
    }
  }

  void enableApplicationForTenant(String tenantId, List applications) {

    Map<String, String> headers = getHttpHeaders(masterTenant)
    headers['x-okapi-token'] = headers['Authorization'].replace('Bearer ', '')

    Map body = [
      tenantId    : tenantId,
      applications: applications
    ]

    String url = Authorization.generateUrl("/entitlements?ignoreErrors=false&purgeOnRollback=true")

    response = restClient.post(url, body, headers)
    if (response.status == 201) {
      logger.info("Application for tenant enabled: ${response.content}")
    } else if (response.status == 400 && response.content.contains("Application is already entitled")) {
      def content = readJSON(text: response.content)
      logger.info("Application is already entitled, no actions needed..\n" + "Status: ${response.status}\n" + "Response content:\n" + writeJSON(json: content, returnText: true, pretty: 2))
      return
    } else {
      logger.error("Enabling application for tenant failed: ${response.content}")
      throw new Exception("Build failed: " + response.content)
    }
  }
}
