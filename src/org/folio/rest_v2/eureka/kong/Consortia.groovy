package org.folio.rest_v2.eureka.kong

import com.cloudbees.groovy.cps.NonCPS
import org.folio.models.EurekaTenantConsortia
import org.folio.rest_v2.eureka.Keycloak
import org.folio.rest_v2.eureka.Kong

class Consortia extends Kong{

  Consortia(def context, String kongUrl, Keycloak keycloak, boolean debug = false){
    super(context, kongUrl, keycloak, debug)
  }

  Consortia(def context, String kongUrl, String keycloakUrl, boolean debug = false){
    super(context, kongUrl, keycloakUrl, debug)
  }

  Consortia(Kong kong){
    this(kong.context, kong.kongUrl, kong.keycloak, kong.restClient.debugValue())
  }

  /**
   * Creates a new consortia.
   *
   * @param centralConsortiaTenant The central tenant of the consortia.
   * @return The ID of the newly created consortia.
   */
  String createConsortia(EurekaTenantConsortia centralConsortiaTenant) {
    if (!centralConsortiaTenant.isCentralConsortiaTenant)
      logger.error("${centralConsortiaTenant.tenantId} is not a central consortia tenant")

    logger.info("Creating consortia with future central tenant ${centralConsortiaTenant.tenantId} and uuid ${centralConsortiaTenant.uuid}...")

    centralConsortiaTenant.consortiaUuid = UUID.randomUUID().toString()

    Map<String, String> headers = getTenantHttpHeaders(centralConsortiaTenant)

    Map body = [
      "id"  : centralConsortiaTenant.consortiaUuid,
      "name": centralConsortiaTenant.consortiaName
    ]

    def response = restClient.post(generateUrl("/consortia"), body, headers)
    Map content = response.body as Map

    logger.info("""
      Info about the newly created consortia \"${content.id}\"
      Status: ${response.responseCode}
      Response content:
      ${content.toString()}""")

    return content.id
  }

  /**
   * Adds a central consortia tenant to a consortia.
   *
   * @param centralConsortiaTenant The central tenant of the consortia.
   */
  Consortia addCentralConsortiaTenant(EurekaTenantConsortia centralConsortiaTenant) {
    if (!centralConsortiaTenant.isCentralConsortiaTenant)
      logger.error("${centralConsortiaTenant.tenantId} is not a central consortia tenant")

    logger.info("Adding central tenant ${centralConsortiaTenant.tenantId} with uuid ${centralConsortiaTenant.uuid} to consortia ${centralConsortiaTenant.consortiaUuid}...")

    Map<String, String> headers = getTenantHttpHeaders(centralConsortiaTenant)

    Map body = [
      "id"       : centralConsortiaTenant.tenantId,
      "name"     : centralConsortiaTenant.tenantName,
      "code"     : centralConsortiaTenant.tenantCode,
      "isCentral": true
    ]

    restClient.post(generateUrl("/consortia/${centralConsortiaTenant.consortiaUuid}/tenants"), body, headers)

    logger.info("${centralConsortiaTenant.tenantId} successfully added to ${centralConsortiaTenant.consortiaName} consortia")

    return this
  }

  /**
   * Adds a tenant to a consortia.
   *
   * @param centralConsortiaTenant The central tenant of the consortia.
   * @param institutionalTenant The tenant to be added to the consortia.
   */
  Consortia addConsortiaTenant(EurekaTenantConsortia centralConsortiaTenant, EurekaTenantConsortia institutionalTenant) {
    if (institutionalTenant.isCentralConsortiaTenant)
      logger.error("${institutionalTenant.tenantId} is a central consortia tenant")

    logger.info("Adding institutional tenant ${institutionalTenant.tenantId} with uuid ${institutionalTenant.uuid} to consortia ${centralConsortiaTenant.consortiaUuid}...")

    centralConsortiaTenant.adminUser.checkUuid()

    Map<String, String> headers = getTenantHttpHeaders(centralConsortiaTenant)

    String url = generateUrl("/consortia/${centralConsortiaTenant.consortiaUuid}/tenants?adminUserId=${centralConsortiaTenant.adminUser.uuid}")

    Map body = [
      "id"       : institutionalTenant.tenantId,
      "name"     : institutionalTenant.tenantName,
      "code"     : institutionalTenant.tenantCode,
      "isCentral": false
    ]

    restClient.post(url, body, headers)

    logger.info("${institutionalTenant.tenantId} successfully added to ${centralConsortiaTenant.consortiaName} consortia")

    return this
  }

  /**
   * Check consortia mapped tenants status
   *
   * @param centralConsortiaTenant
   *
   * @param tenant
   *
   */
  Consortia checkConsortiaStatus(EurekaTenantConsortia centralConsortiaTenant, EurekaTenantConsortia tenant) {
    Map<String, String> headers = getTenantHttpHeaders(centralConsortiaTenant)

    String url = generateUrl("/consortia/${centralConsortiaTenant.consortiaUuid}/tenants/${tenant.tenantId}")

    def response = restClient.get(url, headers, [], 5000).body

    switch (response['setupStatus']) {
      case 'COMPLETED':
        logger.info("Tenant : ${tenant.tenantId} added successfully")
        break
      case 'COMPLETED_WITH_ERRORS':
        logger.warning("Tenant : ${tenant.tenantId} added with errors!")
        break
      case 'FAILED':
        steps.currentBuild.result = 'ABORTED'
        logger.error("Tenant : ${tenant.tenantId} add operation failed!\nAborting current execution!")
        break
      case 'IN_PROGRESS':
        logger.info("Tenant : ${tenant.tenantId} add operation is still in progress...")
        sleep(10000)
        checkConsortiaStatus(centralConsortiaTenant, tenant)
        break
    }

    return this
  }

  @NonCPS
  static Consortia get(Kong kong){
    return new Consortia(kong)
  }
}
