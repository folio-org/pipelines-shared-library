package org.folio.rest_v2.eureka

import org.folio.models.Tenant
import org.folio.rest_v2.Common

class Kong extends Common {

  private Keycloak keycloak

  Kong(Object context, String kongUrl, String keycloakUrl, boolean debug = false) {
    super(context, kongUrl, debug)

    keycloak = new Keycloak(context, keycloakUrl, debug)
  }

  /**
   * Gets the default headers.
   *
   * @return The default headers.
   */
  static Map<String, String> getDefaultHeaders() {
    return [
      "Content-Type": "application/json"
    ]
  }

  /**
   * Generates a URL for the specified path.
   *
   * @param path The path for which to generate the URL.
   * @return The generated URL.
   */
  String generateUrl(String path) {
    "https://${okapiDomain}${path}"
  }

  Map<String, String> getMasterHttpHeaders(boolean addOkapiAuth = false) {
    return getDefaultHeaders() + keycloak.getAuthMasterTenantHeaders(addOkapiAuth)
  }

  Map<String, String> getTenantHttpHeaders(Tenant tenant, boolean addOkapiAuth = false) {
    return getDefaultHeaders() + keycloak.getAuthTenantHeaders(tenant, addOkapiAuth)
  }

  Tenant createTenant(Tenant tenant) {
    logger.info("Creating tenant ${tenant.tenantName}...")

    Map<String, String> headers = getMasterHttpHeaders()

    Map body = [
      name: tenant.tenantName,
      description: tenant.tenantDescription
    ]

    def response = restClient.post(generateUrl("/tenants"), body, headers, [201, 400])
    logger.info(response)
    logger.info(response.body)

    Map<String, String> content = response.body as Map<String, String>

    logger.info("content.toString()=${content.toString()}")

    if (response.responseCode == 400) {
      if (content.containsValue("must match \\\"[a-z][a-z0-9]{0,30}\\\"")) {
        logger.info("""
          Tenant \"${tenant.tenantName}\" is invalid.
          "Status: ${response.responseCode}
          "Response content:
          ${steps.writeJSON(json: content, returnText: true, pretty: 2)}""")

        throw new Exception("Build failed: " + response.content)
      } else if (content.containsValue("Tenant's name already taken")) {
        logger.info("""
          Tenant \"${tenant.tenantName}\" already exists
          Status: ${response.responseCode}
          Response content:
          ${steps.writeJSON(json: content, returnText: true, pretty: 2)}""")

        Tenant existedTenant = getTenantByName(tenant.tenantName)
        logger.info("Continue with existing Eureka tenant id -> ${existedTenant.tenantId}")

        return existedTenant
      } else {
        logger.error("""
          Create new tenant results
          Status: ${response.responseCode}
          Response content:
          ${steps.writeJSON(json: content, returnText: true, pretty: 2)}""")

        throw new Exception("Build failed: " + response.content)
      }
    }

    logger.info("""
      Info on the newly created tenant \"${tenantId}\"
      Status: ${response.responseCode}
      Response content:
      ${steps.writeJSON(json: content, returnText: true, pretty: 2)}""")

    return Tenant.getTenantFromJson(content)
  }

  Tenant getTenant(String tenantId){
    return getTenants(tenantId)[0]
  }

  Tenant getTenantByName(String name){
    return getTenants("", "name==${name}")[0]
  }

  List<Tenant> getTenants(String tenantId = "", String query = ""){
    logger.info("Get tenants${tenantId ? " with tenantId=${tenantId}" : ""}${query ? " with query=${query}" : ""}...")

    Map<String, String> headers = getMasterHttpHeaders()

    String url = generateUrl("/tenants${tenantId ? "/${tenantId}" : ""}${query ? "?query=${query}" : ""}")

    def response = restClient.get(url, headers)
    def content = steps.readJSON(text: response.content)

    if (content.totalRecords > 0) {
      logger.debug("Found tenants: ${content.tenants}")
      List<Tenant> tenants = []
      content.tenants.each { tenantJson ->
        tenants.add(Tenant.getTenantFromJson(tenantJson))
      }
      return tenants
    } else {
      logger.debug("Buy the url ${url} tenant(s) not found")
      logger.debug("HTTP response is: ${response.content}")
      throw new Exception("Tenant(s) not found")
    }
  }

  boolean isTenantExist(String tenantId) {
    return getTenant(tenantId) ? true : false
  }
}
