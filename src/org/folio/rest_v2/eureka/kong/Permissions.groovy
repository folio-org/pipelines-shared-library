package org.folio.rest_v2.eureka.kong

import com.cloudbees.groovy.cps.NonCPS
import org.folio.models.EurekaTenant
import org.folio.models.Role
import org.folio.models.User
import org.folio.models.UserGroup
import org.folio.rest_v2.eureka.Keycloak
import org.folio.rest_v2.eureka.Kong

class Permissions extends Kong{

  Permissions(def context, String kongUrl, Keycloak keycloak, boolean debug = false){
    super(context, kongUrl, keycloak, debug)
  }

  Permissions(def context, String kongUrl, String keycloakUrl, boolean debug = false){
    super(context, kongUrl, keycloakUrl, debug)
  }

  Permissions(Kong kong){
    this(kong.context, kong.kongUrl, kong.keycloak, kong.getDebug())
  }

  Role createRole(EurekaTenant tenant, Role role) {
    logger.info("Creating role for ${tenant.tenantId}...")

    Map<String, String> headers = getTenantHttpHeaders(tenant)

    Map body = role.toMap()

    def response = restClient.post(generateUrl("/roles"), body, headers, [201, 409])
    String contentStr = response.body.toString()
    Map content = response.body as Map

    if (response.responseCode == 409) {
      if (contentStr.contains("HTTP 409 Conflict")) {
        logger.info("""
          Role \"${role.name}\" already exists
          Status: ${response.responseCode}
          Response content:
          ${contentStr}""")

        Role existedRole = getRoleByName(tenant, role.name)

        logger.info("Continue with existing Role uuid -> ${existedRole.uuid}")

        return existedRole
      } else {
        logger.error("""
          Create new role
          Status: ${response.responseCode}
          Response content:
          ${contentStr}""")

        throw new Exception("Build failed: " + contentStr)
      }
    }

    logger.info("""
      Info on the newly created role \"${content.id}\"
      Status: ${response.responseCode}
      Response content:
      ${content.toString()}""")

    return Role.getRoleFromContent(content)
  }

  Role getRole(EurekaTenant tenant, String roleId){
    return getRoles(tenant, roleId)[0]
  }

  Role getRoleByName(EurekaTenant tenant, String name){
    return getRoles(tenant, "", "name==${name}")[0]
  }

  boolean isRoleExist(EurekaTenant tenant, String roleId) {
    return getRoles(tenant, roleId) ? true : false
  }

  List<Role> getRoles(EurekaTenant tenant, String roleId = "", String query = "", limit = 500){
    logger.info("Get roles${roleId ? " with ,roleId=${roleId}" : ""}${query ? " with query=${query}" : ""} for tenant ${tenant.tenantId}...")

    Map<String, String> headers = getTenantHttpHeaders(tenant)

    String url = generateUrl("/roles${roleId ? "/${roleId}" : ""}${query ? "?query=${query}&limit=${limit}" : "?limit=${limit}"}")

    def response = restClient.get(url, headers).body

    if (response.totalRecords > 0) {
      logger.debug("Found roles: ${response.roles}")
      List<Role> roles = []
      response.roles.each { roleContent ->
        roles.add(Role.getRoleFromContent(roleContent as Map))
      }
      return roles
    } else {
      logger.debug("By the url ${url} role(s) not found")
      logger.debug("HTTP response is: ${response}")
      throw new Exception("Role(s) not found")
    }
  }

  List<String> getCapabilitiesId(EurekaTenant tenant, int limit = 5000){
    logger.info("Get capabilities list for ${tenant.tenantId}...")

    Map<String, String> headers = getTenantHttpHeaders(tenant)

    def response = restClient.get(generateUrl("/capabilities?limit=${limit}"), headers)
    def content = response.body.capabilities

    List<String> ids = []

    content.each { capability -> ids.add(capability.id) }

    return ids
  }

  List<String> getCapabilitySetsId(EurekaTenant tenant, int limit = 5000){
    logger.info("Get capability sets list for ${tenant.tenantId}...")

    Map<String, String> headers = getTenantHttpHeaders(tenant)

    def response = restClient.get(generateUrl("/capability-sets?limit=${limit}"), headers)
    def content = response.body.capabilitySets

    List<String> ids = []

    content.each { capabilitySet -> ids.add(capabilitySet.id) }

    return ids
  }

  Permissions assignCapabilitiesToRole(EurekaTenant tenant, Role role, List<String> ids
                                       , boolean skipExists = false, int connectionTimeout = 600000){

    logger.info("Assigning capabilities for role ${role.name}(${role.uuid}) for ${tenant.tenantId}...")

    Map<String, String> headers = getTenantHttpHeaders(tenant)

    Map body = [
      "roleId"       : role.uuid,
      "capabilityIds": ids
    ]

    def response

    if(skipExists)
      response = restClient.post(generateUrl("/roles/capabilities"), body, headers, [201, 400], connectionTimeout)
    else
      response = restClient.post(generateUrl("/roles/capabilities"), body, headers, [], connectionTimeout)

    String contentStr = response.body.toString()

    if (response.responseCode == 400) {
      if (contentStr.contains("type:EntityExistsException")) {
        logger.info("""
          Capabilities already asigned to role \"${role.uuid}\"
          Status: ${response.responseCode}
          Response content:
          ${contentStr}""")

        return this
      } else {
        logger.error("""
          Assigning capabilities to role error
          Status: ${response.responseCode}
          Response content:
          ${contentStr}""")

        throw new Exception("Build failed: " + contentStr)
      }
    }

    return this
  }

  Permissions assignCapabilitySetsToRole(EurekaTenant tenant, Role role, List<String> ids
                                         , boolean skipExists = false, int connectionTimeout = 600000){

    logger.info("Assigning capability sets for role ${role.name}(${role.uuid}) for ${tenant.tenantId}...")

    Map<String, String> headers = getTenantHttpHeaders(tenant)

    Map body = [
      "roleId"          : role.uuid,
      "capabilitySetIds": ids
    ]

    def response

    if(skipExists)
      response = restClient.post(generateUrl("/roles/capability-sets"), body, headers, [201, 400], connectionTimeout)
    else
      response = restClient.post(generateUrl("/roles/capability-sets"), body, headers, [], connectionTimeout)

    String contentStr = response.body.toString()

    if (response.responseCode == 400) {
      if (contentStr.contains("type:EntityExistsException")) {
        logger.info("""
          Capability sets already asigned to role \"${role.uuid}\"
          Status: ${response.responseCode}
          Response content:
          ${contentStr}""")

        return this
      } else {
        logger.error("""
          Assigning capability sets to role error
          Status: ${response.responseCode}
          Response content:
          ${contentStr}""")

        throw new Exception("Build failed: " + contentStr)
      }
    }

    return this
  }

  Permissions assignRolesToUser(EurekaTenant tenant, User user, List<Role> roles){
    logger.info("Assigning roles to user ${user.username}(${user.uuid}) for ${tenant.tenantId}...")

    Map<String, String> headers = getTenantHttpHeaders(tenant)

    List<String> roleIds = []
    roles.each {role -> roleIds.add(role.uuid)}

    Map body = [
      "userId" : user.uuid,
      "roleIds": roleIds
    ]

    def response = restClient.post(generateUrl("/roles/users"), body, headers, [201, 400])
    String contentStr = response.body.toString()

    if (response.responseCode == 400) {
      if (contentStr.contains("Relations between user and roles already exists")) {
        logger.info("""
          Role assignment to the user \"${user.uuid}\" already exists
          Status: ${response.responseCode}
          Response content:
          ${contentStr}""")

        return this
      } else {
        logger.error("""
          Role assignment to the user failed
          Status: ${response.responseCode}
          Response content:
          ${contentStr}""")

        throw new Exception("Build failed: " + contentStr)
      }
    }

    return this
  }

  @NonCPS
  static Permissions get(Kong kong){
    return new Permissions(kong)
  }
}
