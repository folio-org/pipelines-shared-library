package org.folio.rest_v2.eureka.kong

import com.cloudbees.groovy.cps.NonCPS
import org.folio.models.Tenant
import org.folio.models.User
import org.folio.rest_v2.eureka.Keycloak
import org.folio.rest_v2.eureka.Kong

class Users extends Kong{

  Users(def context, String kongUrl, Keycloak keycloak, boolean debug = false){
    super(context, kongUrl, keycloak, debug)
  }

  Users(def context, String kongUrl, String keycloakUrl, boolean debug = false){
    super(context, kongUrl, keycloakUrl, debug)
  }

  Users(Kong kong){
    this(kong.context, kong.kongUrl, kong.keycloak, kong.restClient.debugValue())
  }

  User createUser(Tenant tenant, User user) {
    logger.info("Creating user for ${tenant.tenantName}...")

    Map<String, String> headers = getTenantHttpHeaders(tenant)

    Map body = user.toMap()

    def response = restClient.post(generateUrl("/users-keycloak/users"), body, headers)
    Map content = response.body as Map

    logger.info("""
      Info on the newly created user \"${content.id}\"
      Status: ${response.responseCode}
      Response content:
      ${content.toString()}""")

    return User.getUserFromContent(content, tenant, this as UserGroups)
  }

  User getUser(Tenant tenant, String userId){
    return getUsers(tenant, userId)[0]
  }

  User getUserByName(Tenant tenant, String name){
    return getUsers(tenant, "", "name==${name}")[0]
  }

  boolean isUserExist(Tenant tenant, String userId) {
    return getUser(tenant, userId) ? true : false
  }

  List<User> getUsers(Tenant tenant, String userId = "", String query = ""){
    logger.info("Get users${userId ? " with ,userId=${userId}" : ""}${query ? " with query=${query}" : ""} for tenant ${tenant.tenantName}...")

    Map<String, String> headers = getTenantHttpHeaders(tenant)

    String url = generateUrl("/users${userId ? "/${userId}" : ""}${query ? "?query=${query}" : ""}")

    Map response = restClient.get(url, headers).body as Map

    if (response.totalRecords > 0) {
      logger.debug("Found users: ${response.users}")
      List<User> users = []
      response.users.each { userContent ->
        users.add(User.getUserFromContent(userContent as Map, tenant, this as UserGroups))
      }
      return users
    } else {
      logger.debug("Buy the url ${url} user(s) not found")
      logger.debug("HTTP response is: ${response}")
      throw new Exception("User(s) not found")
    }
  }

  User setUpdatePassword(Tenant tenant, User user){
    logger.info("Setting or updating user password for user ${user.username}(${user.uuid}) for ${tenant.tenantName}...")

    Map<String, String> headers = getTenantHttpHeaders(tenant)

    Map body = [
      "username": user.username,
      "userId": user.uuid,
      "password": user.password.getPlainText()
    ]

    def response = restClient.post(generateUrl("/authn/credentials"), body, headers, [201, 400])
    Map content = response.body as Map

    if (response.responseCode == 400 && content.toString().contains("already exists credentials")) {
      logger.info("""
        Credential for user \"${user.uuid}\" already exists
        Status: ${response.responseCode}
        Response content:
        ${content.toString()}""")

      return setUpdatePassword(tenant, unsetPassword(tenant, user))
     }

    return user
  }

  User unsetPassword(Tenant tenant, User user){
    logger.info("Unset user password for user ${user.username}(${user.uuid}) for ${tenant.tenantName}...")

    Map<String, String> headers = getTenantHttpHeaders(tenant)

    restClient.delete(generateUrl("/authn/credentials?userId=${user.uuid}"), headers)

    return user
  }

  @NonCPS
  static Users get(Kong kong){
    return new Users(kong)
  }
}
