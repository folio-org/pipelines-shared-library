package org.folio.rest_v2.eureka.kong

import com.cloudbees.groovy.cps.NonCPS
import hudson.util.Secret
import org.folio.models.EurekaTenant
import org.folio.rest_v2.eureka.Keycloak
import org.folio.rest_v2.eureka.Kong
import org.folio.models.FolioModule

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

  List<EurekaTenant> getTenants(String tenantId = "", String query = "", int limit = 500){
    logger.info("Get tenants${tenantId ? " with tenantId=${tenantId}" : ""}${query ? " with query=${query}" : ""}...")

    Map<String, String> headers = getMasterHttpHeaders()

    String url = generateUrl("/tenants${tenantId ? "/${tenantId}" : ""}${query ? "?query=${query}&limit=${limit}" : "?limit=${limit}"}")

    logger.debug("Get tenants url: $url")

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
      tenantId: tenant.uuid,
      applications: tenant.applications.values()
    ]

    logger.debug("enableApplicationsOnTenant body: ${body}")
    logger.debug("enableApplicationsOnTenant tenant.applications: ${tenant.applications}")
    logger.debug("enableApplicationsOnTenant install params: ${tenant.getInstallRequestParams()?.toQueryString()}")

    def response = restClient.post(
      generateUrl("/entitlements${tenant.getInstallRequestParams()?.toQueryString() ?: ''}")
      , body
      , headers
      , [201, 400]
    )

    logger.debug("enableApplicationsOnTenant after request")

    String contentStr = response.body.toString()

    if (response.responseCode == 400) {
      if (contentStr.contains("value: Entitle flow finished")) {
        logger.info("""
          Application is already entitled, no actions needed..
          Status: ${response.responseCode}
          Response content:
          ${contentStr}""")

        return this
      } else {
        logger.error("Enabling application for tenant failed: ${contentStr}")

        throw new Exception("Build failed: " + contentStr)
      }
    }

    logger.info("Enabling (entitle) applications on tenant ${tenant.tenantId} with ${tenant.uuid} were finished successfully")

    return this
  }

  @NonCPS
  static Tenants get(Kong kong){
    return new Tenants(kong)
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
  Map getEnabledApplications(EurekaTenant tenant, String query = "", Boolean includeModules = false, int limit = 500) {
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

      return content['entitlements'].collectEntries { entitlement -> [entitlement.applicationId, entitlement] }
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
   * Check if specific application is enabled
   * @param tenant EurekaTenant instance
   * @param appId application id (e.g. app-platform-full-1.0.0-SNAPSHOT.176)
   * @return boolean true if application is enabled (entitled)
   */
  boolean isApplicationEnabled(EurekaTenant tenant, String appId) {
    return getEnabledApplications(tenant, "applicationId=${appId}").size() > 0
  }

  /**
   * Get specific enabled application
   * @param tenant EurekaTenant instance
   * @param appId application id (e.g. app-platform-full-1.0.0-SNAPSHOT.176)
   * @return Map with enabled (entitled) application
   */
  Map getEnabledApplicationById(EurekaTenant tenant, String appId, Boolean includeModules = false){
    return getEnabledApplications(tenant,"applicationId=${appId}", includeModules)[0]
  }

  /**
   * Get specific enabled application
   * @param tenant EurekaTenant instance
   * @param tenantUuid Tenant UUID (e.g. 75fdaeb7-0027-41fc-a0c5-b8d170c08722)
   * @return Map with enabled (entitled) application
   */
  Map getEnabledApplicationByTenantUuid(EurekaTenant tenant, String tenantUuid, Boolean includeModules = false){
    return getEnabledApplications(tenant,"tenantId=${tenantUuid}", includeModules)[0]
  }

  /**
   * Get Eureka Applications Enabled for Tenant with Specific Module.
   * @param tenant instance
   * @param module instance
   * @return Map of Entitled Applications with Specific Module.
   */
  Map getEnabledApplicationsWithModule(EurekaTenant tenant, FolioModule module) {
    logger.info("Get enabled applications on tenant ${tenant.tenantId} with ${module.id} module...")

    Map enabledApps = this.getEnabledApplications(tenant, '', true)

    Map enabledAppsWithModule = enabledApps.findAll {application ->
      application.value.modules.any { it.startsWith(module.name) }
    }

    if (enabledAppsWithModule != null) {
      logger.info("""
        Enabled applications on tenant ${tenant.tenantId} contains module ${module.name}:
        Response content:
        ${enabledAppsWithModule}""")

      return enabledAppsWithModule
    } else {
      logger.warning("Enabled applications on tenant ${tenant.tenantId} don't contain module ${module.name}")
    }
  }
}
