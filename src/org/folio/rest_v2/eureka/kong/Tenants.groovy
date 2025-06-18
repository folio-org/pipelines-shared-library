package org.folio.rest_v2.eureka.kong

import com.cloudbees.groovy.cps.NonCPS
import org.folio.models.EurekaTenant
import org.folio.models.application.Application
import org.folio.models.application.ApplicationList
import org.folio.rest_v2.eureka.Keycloak
import org.folio.rest_v2.eureka.Kong

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

    sleep(10000) //On large number of tenants, the tenant is not created immediately!!!

    return EurekaTenant.getTenantFromContent(content)
  }

  EurekaTenant getTenant(String tenantId){
    return getTenants(tenantId)[0]
  }

  EurekaTenant getTenantByName(String name){
    return getTenants("", "name==${name}")[0]
  }

  List<EurekaTenant> getTenants(String tenantId = "", String query = "", int limit = 500){
    logger.info("Get tenants${tenantId ? " with tenantId=${tenantId}" : ""}${query ? " with query=${query}" : ""}...")

    Map<String, String> headers = getMasterHttpHeaders()

    String url = generateUrl("/tenants${tenantId ? "/${tenantId}" : ""}${query ? "?query=${query}&limit=${limit}" : "?limit=${limit}"}")

    logger.debug("Get tenants url: $url")

    context.input(message: "Let's check endpoint")

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

  /**
   * Enable (entitle) applications on tenant.
   *
   * @param tenant EurekaTenant instance.
   * @param appIds List of application ids to be enabled.
   * @param skipExistence boolean flag to skip error if apps were already enabled.
   * @return Tenants instance.
   */
  Tenants enableApplications(EurekaTenant tenant, List<String> appIds, boolean skipExistence = false){
    logger.info("Enable (entitle) applications with ids: ${appIds} on tenant ${tenant.tenantId} with ${tenant.uuid}...")

    if(!appIds)
      return this

    Map<String, String> headers = getMasterHttpHeaders(true)

    Map body = [
      tenantId    : tenant.uuid,
      applications: appIds
    ]

    List responseCodes = skipExistence ? [201, 400] : []

    def response = restClient.post(
      generateUrl("/entitlements${tenant.getInstallRequestParams()?.toQueryString() ?: ''}")
      , body
      , headers
      , responseCodes
    )

    String contentStr = response.body.toString()

    if (response.responseCode == 400) {
      if (contentStr.contains("value: Entitle flow finished")) {
        logger.info("""
          Application(s) are already entitled on tenant, no actions needed..
          Status: ${response.responseCode}
          Response content:
          ${contentStr}""")
      } else {
        logger.error("Enabling application for tenant failed: ${contentStr}")

        throw new Exception("Build failed: " + contentStr)
      }
    }

    logger.info("Enabling (entitle) applications on tenant ${tenant.tenantId} with ${tenant.uuid} was finished successfully")

    return this
  }

  /**
   * Update applications on tenant.
   *
   * @param tenant EurekaTenant instance.
   * @param appIds List of application ids to be updated.
   * @return Tenants instance.
   */
  Tenants updateApplications(EurekaTenant tenant, List<String> appIds) {
    if(!appIds)
      return this

    logger.info("Update the following applications ${appIds} on tenant ${tenant.tenantId} with ${tenant.uuid}...")

    Map<String, String> headers = getMasterHttpHeaders(true)

    Map body = [
      tenantId    : tenant.uuid,
      applications: appIds
    ]

    restClient.put(
      generateUrl("/entitlements${tenant.getInstallRequestParams()?.toQueryString() ?: ''}")
      , body
      , headers
    )

    logger.info("Update the following applications ${appIds} on tenant ${tenant.tenantId} with ${tenant.uuid} was finished successfully")

    return this
  }

  /**
   * Get Eureka Applications Enabled (entitled) for Tenant.
   *
   * @param tenant EurekaTenant instance.
   * @param query CQL query.
   * @param includeModules boolean flag to include modules.
   * @param limit number of records to return in response.
   * @return Map of Entitled Applications.
   */
  ApplicationList getEnabledApplications(EurekaTenant tenant, String query = "", boolean includeModules = false, int limit = 500) {
    String pathParams = "query=${query ?: "tenantId=${tenant.uuid}"}&includeModules=${includeModules}&limit=${limit}"

    logger.info("Get enabled (entitled) applications for ${tenant.tenantId} tenant with parameters: ${pathParams}...")

    Map<String, String> headers = getMasterHttpHeaders(true)

    def response = restClient.get(generateUrl("/entitlements?${pathParams}"), headers)

    String contentStr = response.body.toString()
    Map content = response.body as Map

    if (response.responseCode == 200) {
      logger.info("""
        Enabled applications on tenant ${tenant.tenantId}:
        Status: ${response.responseCode}
        Response content:
        ${contentStr}""")

      ApplicationList applicationList = new ApplicationList()
      applicationList.addAll(
        (content['entitlements'] as List<Map>)
          .collect { entitlement ->
            new Application(entitlement.applicationId as String)
              .withModulesIds(entitlement.modules as List<String>)
          }
      )

      return applicationList
    } else {
      logger.error("""
        Get enabled applications on tenant ${tenant.tenantId} failed
        Status: ${response.responseCode}
        Response content:
        ${contentStr}""")

      throw new Exception("Build failed: " + contentStr)
    }
  }

  /**
   * Check if application is enabled (entitled) for tenant.
   *
   * @param tenant EurekaTenant instance.
   * @param appId application id (e.g. app-platform-full-1.0.0-SNAPSHOT.176).
   * @return boolean flag indicating if application is enabled.
   */
  boolean isApplicationEnabled(EurekaTenant tenant, String appId) {
    return getEnabledApplications(tenant, "applicationId=${appId}").size() > 0
  }

  /**
   * Get specific enabled application
   * @param tenant EurekaTenant instance
   * @param appId Application id (e.g. app-platform-full-1.0.0-SNAPSHOT.176)
   * @return Application instance with specificId
   */
  Application getEnabledApplicationById(EurekaTenant tenant, String appId, boolean includeModules = false){
    ApplicationList enabledApps = getEnabledApplications(tenant,"applicationId=${appId}", includeModules)

    return enabledApps ? enabledApps[0]: null
  }

  /**
   * Get enabled applications for specific tenant.
   *
   * @param tenant EurekaTenant instance of a specific tenant.
   * @param includeModules boolean flag to include modules.
   * @return ApplicationList of enabled applications.
   */
  ApplicationList getEnabledApplicationOnTenant(EurekaTenant tenant, boolean includeModules = false){
    return getEnabledApplications(tenant,"tenantId=${tenant.uuid}", includeModules)
  }

  @NonCPS
  static Tenants get(Kong kong){
    return new Tenants(kong)
  }
}
