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
    String contentStr = response.body.toString()
    Map content = response.body as Map

    if (response.responseCode == 400) {
      if (contentStr.contains("must match \\\"[a-z][a-z0-9]{0,30}\\\"")) {
        logger.info("""
          Tenant \"${tenant.tenantName}\" is invalid.
          "Status: ${response.responseCode}
          "Response content:
          ${contentStr}""")

        throw new Exception("Build failed: " + response.contentStr)
      } else if (contentStr.contains("Tenant's name already taken")) {
        logger.info("""
          Tenant \"${tenant.tenantName}\" already exists
          Status: ${response.responseCode}
          Response content:
          ${contentStr}""")

        Tenant existedTenant = getTenantByName(tenant.tenantName)
        logger.info("Continue with existing Eureka tenant id -> ${existedTenant.tenantId}")

        return existedTenant
      } else {
        logger.error("""
          Create new tenant results
          Status: ${response.responseCode}
          Response content:
          ${contentStr}""")

        throw new Exception("Build failed: " + response.contentStr)
      }
    }

    logger.info("""
      Info on the newly created tenant \"${content.id}\"
      Status: ${response.responseCode}
      Response content:
      ${contentStr}""")

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

    def response = restClient.get(url, headers).body

    if (response.totalRecords > 0) {
      logger.debug("Found tenants: ${response.tenants}")
      List<Tenant> tenants = []
      response.tenants.each { tenantJson ->
        tenants.add(Tenant.getTenantFromJson(tenantJson))
      }
      return tenants
    } else {
      logger.debug("Buy the url ${url} tenant(s) not found")
      logger.debug("HTTP response is: ${response}")
      throw new Exception("Tenant(s) not found")
    }
  }

  boolean isTenantExist(String tenantId) {
    return getTenant(tenantId) ? true : false
  }

  void enableApplicationsOnTenant(Tenant tenant, List<String> applications) {
    logger.info("Enable (entitle) applications on tenant ${tenant.tenantName} with ${tenant.tenantId}...")

    Map<String, String> headers = getMasterHttpHeaders(true)

    Map body = [
      tenantId: tenant.tenantId,
      applications: applications
    ]

    def response = restClient.post(
      generateUrl("/entitlements?purgeOnRollback=true&ignoreErrors=false")
      , body
      , headers
      , [201, 400]
    )

    String contentStr = response.body.toString()
    Map content = response.body as Map

    if (response.responseCode == 400) {
      if (contentStr.contains("Application is already entitled")) {
        logger.info("""
          Application is already entitled, no actions needed..
          Status: ${response.responseCode}
          Response content:
          ${contentStr}""")

        throw new Exception("Build failed: " + contentStr)
      } else {
        logger.error("Enabling application for tenant failed: ${contentStr}")

        throw new Exception("Build failed: " + contentStr)
      }
    }

    logger.info("Enabling (entitle) applications on tenant ${tenant.tenantName} with ${tenant.tenantId} were finished successfully")
  }
}
