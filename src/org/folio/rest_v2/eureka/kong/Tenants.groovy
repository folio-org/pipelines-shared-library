package org.folio.rest_v2.eureka.kong

import com.cloudbees.groovy.cps.NonCPS
import hudson.util.Secret
import org.folio.models.EurekaTenant
import org.folio.rest_v2.eureka.Keycloak
import org.folio.rest_v2.eureka.Kong
import org.folio.Constants

class Tenants extends Kong{

  Tenants(def context, String kongUrl, Keycloak keycloak, boolean debug = false){
    super(context, kongUrl, keycloak, debug)
  }

  Tenants(def context, String kongUrl, String keycloakUrl, boolean debug = false){
    super(context, kongUrl, keycloakUrl, debug)
  }

  Tenants(Kong kong){
    this(kong.context, kong.kongUrl, kong.keycloak, kong.getDebug())
  }

  EurekaTenant createTenant(EurekaTenant tenant) {
    logger.info("Creating tenant ${tenant.tenantId}...")

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

        EurekaTenant existedTenant = getTenantByName(tenant.tenantId)

        logger.info("Continue with existing Eureka tenant id -> ${existedTenant.uuid}")

        return existedTenant
      } else {
        logger.error("""
          Create new tenant results
          Status: ${response.responseCode}
          Response content:
          ${contentStr}""")

        throw new Exception("Build failed: " + contentStr)
      }
    }

    logger.info("""
      Info on the newly created tenant \"${content.id}\"
      Status: ${response.responseCode}
      Response content:
      ${contentStr}""")

    return EurekaTenant.getTenantFromContent(content)
  }

  EurekaTenant getTenant(String tenantId){
    return getTenants(tenantId)[0]
  }

  EurekaTenant getTenantByName(String name){
    return getTenants("", "name==${name}")[0]
  }

  List<EurekaTenant> getTenants(String tenantId = "", String query = ""){
    logger.info("Get tenants${tenantId ? " with tenantId=${tenantId}" : ""}${query ? " with query=${query}" : ""}...")

    Map<String, String> headers = getMasterHttpHeaders()

    String url = generateUrl("/tenants${tenantId ? "/${tenantId}" : ""}${query ? "?query=${query}" : ""}")

    def response = restClient.get(url, headers).body

    if (response.totalRecords > 0) {
      logger.debug("Found tenants: ${response.tenants}")
      List<EurekaTenant> tenants = []
      response.tenants.each { tenantContent ->
        tenants.add(EurekaTenant.getTenantFromContent(tenantContent as Map))
      }
      return tenants
    } else {
      logger.debug("By the url ${url} tenant(s) not found")
      logger.debug("HTTP response is: ${response}")
      throw new Exception("Tenant(s) not found")
    }
  }

  boolean isTenantExist(String tenantId) {
    return getTenant(tenantId) ? true : false
  }

  Tenants enableApplicationsOnTenant(EurekaTenant tenant) {
    logger.info("Enable (entitle) applications on tenant ${tenant.tenantId} with ${tenant.uuid}...")

    Map<String, String> headers = getMasterHttpHeaders(true)

    Map body = [
      tenantId: tenant.tenantId,
      applications: tenant.applications.values()
    ]

    logger.debug("enableApplicationsOnTenant body: ${body}")
    logger.debug("enableApplicationsOnTenant install params: ${tenant.getInstallRequestParams()?.toQueryString()}")

    def response = restClient.post(
      generateUrl("/entitlements")
      , body
      , headers
      , [201, 400]
    )

    logger.debug("enableApplicationsOnTenant after request")

    String contentStr = response.body.toString()

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

    logger.info("Enabling (entitle) applications on tenant ${tenant.tenantId} with ${tenant.uuid} were finished successfully")
  }

  @NonCPS
  static Tenants get(Kong kong){
    return new Tenants(kong)
  }
}
