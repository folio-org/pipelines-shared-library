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
    if (isTenantExist(tenant.tenantId)) {
      logger.warning("Tenant ${tenant.tenantId} already exists!")
      return
    }

    String url = generateUrl("/tenants")  // Tenant Manager URL
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
}
