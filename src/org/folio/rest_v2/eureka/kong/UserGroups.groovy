package org.folio.rest_v2.eureka.kong

import com.cloudbees.groovy.cps.NonCPS
import org.folio.models.Tenant
import org.folio.models.UserGroup
import org.folio.rest_v2.eureka.Keycloak
import org.folio.rest_v2.eureka.Kong

class UserGroups extends Kong{

  UserGroups(def context, String kongUrl, Keycloak keycloak, boolean debug = false){
    super(context, kongUrl, keycloak, debug)
  }

  UserGroups(def context, String kongUrl, String keycloakUrl, boolean debug = false){
    super(context, kongUrl, keycloakUrl, debug)
  }

  UserGroups(Kong kong){
    this(kong.context, kong.kongUrl, kong.keycloak, kong.restClient.debug)
  }

  UserGroup createUserGroup(Tenant tenant, UserGroup group) {
    logger.info("Creating user group for ${tenant.tenantName}...")

    Map<String, String> headers = getTenantHttpHeaders(tenant)

    Map body = group.toMap()

    def response = restClient.post(generateUrl("/groups"), body, headers)
    Map content = response.body as Map

    logger.info("""
      Info on the newly created user group \"${content.id}\"
      Status: ${response.responseCode}
      Response content:
      ${content.toString()}""")

    return UserGroup.getGroupFromContent(content)
  }

  UserGroup getUserGroup(Tenant tenant, String groupId){
    return getUserGroups(tenant, groupId)[0]
  }

  UserGroup getUserGroupByName(Tenant tenant, String name){
    return getUserGroups(tenant, "", "name==${name}")[0]
  }

  boolean isUserGroupExist(Tenant tenant, String groupId) {
    return getUserGroup(tenant, groupId) ? true : false
  }

  List<UserGroup> getUserGroups(Tenant tenant, String groupId = "", String query = ""){
    logger.info("Get user groups${groupId ? " with ,groupId=${groupId}" : ""}${query ? " with query=${query}" : ""} for tenant ${tenant.tenantName}...")

    Map<String, String> headers = getTenantHttpHeaders(tenant)

    String url = generateUrl("/groups${groupId ? "/${groupId}" : ""}${query ? "?query=${query}" : ""}")

    def response = restClient.get(url, headers).body

    if (response.totalRecords > 0) {
      logger.debug("Found user groups: ${response.usergroups}")
      List<UserGroup> groups = []
      response.usergroups.each { groupContent ->
        groups.add(UserGroup.getGroupFromContent(groupContent as Map))
      }
      return groups
    } else {
      logger.debug("Buy the url ${url} user group(s) not found")
      logger.debug("HTTP response is: ${response}")
      throw new Exception("User group(s) not found")
    }
  }

  @NonCPS
  static UserGroups get(Kong kong){
    return new UserGroups(kong)
  }
}
