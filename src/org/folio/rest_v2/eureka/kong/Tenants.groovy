package org.folio.rest_v2.eureka.kong

import org.folio.models.Tenant
import org.folio.rest_v2.eureka.Keycloak
import org.folio.rest_v2.eureka.Kong

class Tenants extends Kong<Tenants>{

  protected Tenants(def context, String kongUrl, Keycloak keycloak, boolean debug = false){
    super(context, kongUrl, keycloak, debug)
  }

  Tenant createTenant(Tenant tenant) {
    logger.info("Creating tenant ${tenant.tenantName}...")

    Map<String, String> headers = getMasterHttpHeaders()

    Map body = tenant.toMap()

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

    return Tenant.getTenantFromContent(content)
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
      response.tenants.each { tenantContent ->
        tenants.add(Tenant.getTenantFromContent(tenantContent))
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
      if (contentStr.contains("finished with status: CANCELLED")) {
        logger.info("""
          Application is already entitled, no actions needed..
          Status: ${response.responseCode}
          Response content:
          ${contentStr}""")

        return
      } else {
        logger.error("Enabling application for tenant failed: ${contentStr}")

        throw new Exception("Build failed: " + contentStr)
      }
    }

    logger.info("Enabling (entitle) applications on tenant ${tenant.tenantName} with ${tenant.tenantId} were finished successfully")
  }
}
