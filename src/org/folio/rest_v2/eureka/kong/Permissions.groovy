package org.folio.rest_v2.eureka.kong

import com.cloudbees.groovy.cps.NonCPS
import org.folio.models.EurekaTenant
import org.folio.models.Role
import org.folio.models.User
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
    this(kong.context, kong.kongUrl, kong.keycloak, kong.restClient.debugValue())
  }

  Role createRole(EurekaTenant tenant, Role role) {
    logger.info("Creating role for ${tenant.tenantId}...")

    Map<String, String> headers = getTenantHttpHeaders(tenant)

    Map body = role.toMap()

    def response = restClient.post(generateUrl("/roles"), body, headers)
    Map content = response.body as Map

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

  List<Role> getRoles(EurekaTenant tenant, String roleId = "", String query = ""){
    logger.info("Get roles${roleId ? " with ,roleId=${roleId}" : ""}${query ? " with query=${query}" : ""} for tenant ${tenant.tenantId}...")

    Map<String, String> headers = getTenantHttpHeaders(tenant)

    String url = generateUrl("/roles${roleId ? "/${roleId}" : ""}${query ? "?query=${query}" : ""}")

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

  List<String> getCapabilitiesId(EurekaTenant tenant, int limit = 3000){
    logger.info("Get capabilities list for ${tenant.tenantId}...")

    Map<String, String> headers = getTenantHttpHeaders(tenant)

    def response = restClient.get(generateUrl("/capabilities?limit=${limit}"), headers)
    Map content = response.body.capabilities as Map

    return content.each { capability -> capability.id } as List<String>
  }

  List<String> getCapabilitySetsId(EurekaTenant tenant, int limit = 3000){
    logger.info("Get capability sets list for ${tenant.tenantId}...")

    Map<String, String> headers = getTenantHttpHeaders(tenant)

    def response = restClient.get(generateUrl("/capability-sets?limit=${limit}"), headers)
    Map content = response.body.capabilitySets as Map

    return content.each { set -> set.id } as List<String>
  }

  Permissions assignCapabilitiesToRole(EurekaTenant tenant, Role role, List<String> ids){
    logger.info("Assigning capabilities for role ${role.name}(${role.uuid}) for ${tenant.tenantId}...")

    Map<String, String> headers = getTenantHttpHeaders(tenant)

    Map body = [
      "roleId": role.uuid,
      "capabilityIds": ids
    ]

    restClient.post(generateUrl("/roles/capabilities"), body, headers)

    return this
  }

  Permissions assignCapabilitySetsToRole(EurekaTenant tenant, Role role, List<String> ids){
    logger.info("Assigning capability sets for role ${role.name}(${role.uuid}) for ${tenant.tenantId}...")

    Map<String, String> headers = getTenantHttpHeaders(tenant)

    Map body = [
      "roleId": role.uuid,
      "capabilitySetIds": ids
    ]

    restClient.post(generateUrl("/roles/capability-sets"), body, headers)

    return this
  }

  Permissions assignRolesToUser(EurekaTenant tenant, User user, List<Role> roles){
    logger.info("Assigning roles to user ${user.username}(${user.uuid}) for ${tenant.tenantId}...")

    Map<String, String> headers = getTenantHttpHeaders(tenant)

    Map body = [
      "userId": user.uuid,
      "roleIds": roles
    ]

    restClient.post(generateUrl("/roles/users"), body, headers)

    return this
  }

  @NonCPS
  static Permissions get(Kong kong){
    return new Permissions(kong)
  }
}
