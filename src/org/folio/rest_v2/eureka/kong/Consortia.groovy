package org.folio.rest_v2.eureka.kong

import com.cloudbees.groovy.cps.NonCPS
import org.folio.models.EurekaTenant
import org.folio.models.EurekaTenantConsortia
import org.folio.models.Role
import org.folio.models.User
import org.folio.rest_v2.eureka.Keycloak
import org.folio.rest_v2.eureka.Kong

class Consortia extends Kong {

  Consortia(def context, String kongUrl, Keycloak keycloak, boolean debug = false) {
    super(context, kongUrl, keycloak, debug)
  }

  Consortia(Kong kong) {
    this(kong.context, kong.kongUrl, kong.keycloak, kong.getDebug())
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

    def response = restClient.post(generateUrl("/consortia"), body, headers, [201, 409])
    String contentStr = response.body.toString()
    Map content = response.body as Map

    if (response.responseCode == 409) {
      logger.info("""
        Consortia already exists
        Status: ${response.responseCode}
        Response content:
        ${contentStr}""")

      centralConsortiaTenant.consortiaUuid = getConsortiaID(centralConsortiaTenant)

      logger.info("Continue with existing consortia id -> ${centralConsortiaTenant.consortiaUuid}")

      return centralConsortiaTenant.consortiaUuid
    }

    logger.info("""
      Info about the newly created consortia \"${content.id}\"
      Status: ${response.responseCode}
      Response content:
      ${content.toString()}""")

    return content.id
  }

  String getConsortiaID(EurekaTenant centralConsortiaTenant) {
    logger.info("Get tenant's ${centralConsortiaTenant.getTenantId()} ${centralConsortiaTenant.getUuid()} consortia ID ...")

    Map<String, String> headers = getTenantHttpHeaders(centralConsortiaTenant, true)

    def response = restClient.get(generateUrl("/consortia"), headers).body

    if (response.totalRecords == 1) {
      logger.debug("Found consortia: ${response.consortia}")

      return response.consortia[0].id
    } else if (response.totalRecords > 1) {
      logger.debug("${response.totalRecords} consortias have been found")
      logger.debug("HTTP response is: ${response}")
      throw new Exception("Too many consortias")
    } else {
      logger.debug("Consortia hasn't been found")
      logger.debug("HTTP response is: ${response}")
      throw new Exception("Consortia(s) not found")
    }
  }

  /**
   * Checks if a tenant is a consortia tenant via the consortia-configuration endpoint and returns TenantConsortiaConfiguration
   *
   * @param tenant The tenant to check.
   * @return consortia tenant configuration, null otherwise.
   */
  TenantConsortiaConfiguration getTenantConsortiaConfiguration(EurekaTenant tenant){
    logger.info("Check if tenant ${tenant.getTenantId()} ${tenant.getUuid()} is a consortia tenant ...")

    Map<String, String> headers = getTenantHttpHeaders(tenant, true)

    try {
      def response = restClient.get(generateUrl("/consortia-configuration"), headers)
      Map content = response.body as Map

      return new TenantConsortiaConfiguration(content.id as String, content.centralTenantId as String)
    }catch (Exception ignored){
      logger.debug("Tenant is not a consortia tenant")

      return null
    }
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

    def response = restClient.post(generateUrl("/consortia/${centralConsortiaTenant.consortiaUuid}/tenants"), body, headers, [201, 409])
    String contentStr = response.body.toString()

    if (response.responseCode == 409)
      logger.info("""
        Central consortia tenant already added
        Status: ${response.responseCode}
        Response content:
        ${contentStr}""")
    else
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

    logger.info("""Adding institutional tenant ${institutionalTenant.tenantId} with uuid ${institutionalTenant.uuid}
          with tenant name ${institutionalTenant.tenantName} and code ${institutionalTenant.tenantCode}
          to consortia ${centralConsortiaTenant.consortiaUuid} and adminUserId ${centralConsortiaTenant.adminUser.uuid}...
    """)

    centralConsortiaTenant.adminUser.checkUuid()

    Map<String, String> headers = getTenantHttpHeaders(centralConsortiaTenant)

    String url = generateUrl("/consortia/${centralConsortiaTenant.consortiaUuid}/tenants?adminUserId=${centralConsortiaTenant.adminUser.uuid}")

    Map body = [
      "id"       : institutionalTenant.tenantId,
      "name"     : institutionalTenant.tenantName,
      "code"     : institutionalTenant.tenantCode,
      "isCentral": false
    ]

    def response = restClient.post(url, body, headers, [201, 409, 500])

    String contentStr = response.body.toString()

    switch (response.responseCode) {
      case 201:
        logger.info("""
          Tenant : ${institutionalTenant.tenantId} added successfully
          Status: ${response.responseCode}
          Response content:
          ${contentStr}""")
        break
      case 409:
        logger.info("""
          Tenant : ${institutionalTenant.tenantId} already added
          Status: ${response.responseCode}
          Response content:
          ${contentStr}""")
        break
      case 500:
        logger.info("""
          Tenant : ${institutionalTenant.tenantId} add operation failed!
          Try to delete the tenant and re-add it operation started...""")
          def fix = restClient.delete(generateUrl("/consortia/${centralConsortiaTenant.consortiaUuid}/tenants/${institutionalTenant.tenantId}"), headers, [204, 404])
          if (fix.responseCode == 204) {
            logger.info("Tenant : ${institutionalTenant.tenantId} deleted successfully from consortia.\nTrying to add it again...")
            addConsortiaTenant(centralConsortiaTenant, institutionalTenant)
          } else {
            logger.warning("Adding tenant : ${institutionalTenant.tenantId} in consortia operation end with errors!\nContinue with current execution...")
          }
        break
    }
    sleep(10000)

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

  void addRoleToShadowAdminUser(EurekaTenantConsortia centralConsortiaTenant, EurekaTenantConsortia tenant, boolean execute = false) {

    if (execute) {

      sleep(10000) // wait for tenant to be ready in consortia

      Role role = Permissions.get(this).getRoleByName(tenant, "adminRole")
      User user = Users.get(this).getUserByUsername(tenant, centralConsortiaTenant.getAdminUser().getUsername())

      logger.info("""
                   Task: Add admin role to shadow admin user
                   user: ${user.username}
                   tenant: ${tenant.tenantId}
                  """)

      Permissions.get(this).assignRolesToUser(tenant, user, [role], true)

      logger.info("Task: Add admin role to shadow admin ${user.username} in tenant ${tenant.tenantId} completed")

    }

  }

  @NonCPS
  static Consortia get(Kong kong) {
    return new Consortia(kong)
  }
}
