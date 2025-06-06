package org.folio.rest_v2.eureka.kong

import com.cloudbees.groovy.cps.NonCPS
import org.folio.models.EurekaTenant
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
    this(kong.context, kong.kongUrl, kong.keycloak, kong.getDebug())
  }

  User createUser(EurekaTenant tenant, User user) {
    logger.info("Creating user for ${tenant.tenantId}...")

    Map<String, String> headers = getTenantHttpHeaders(tenant)

    Map body = user.toMap()

    def response = restClient.post(generateUrl("/users-keycloak/users"), body, headers)
    Map content = response.body as Map

    logger.info("""
      Info on the newly created user \"${content.id}\"
      Status: ${response.responseCode}
      Response content:
      ${content.toString()}""")

    return User.getUserFromContent(content, tenant, UserGroups.get(this))
  }

  User getUser(EurekaTenant tenant, String userId){
    return getUsers(tenant, "id==${userId}")[0]
  }

  User getUserByName(EurekaTenant tenant, String name){
    return getUsers(tenant, "name==${name}")[0]
  }

  User getUserByUsername(EurekaTenant tenant, String username){
    return getUsers(tenant, "username=${username}")[0]
  }

  boolean isUserExist(EurekaTenant tenant, String userId) {
    return getUser(tenant, userId) ? true : false
  }

  List<User> getUsers(EurekaTenant tenant, String query = "", int limit = 3000){
    logger.info("Get users${query ? " with query=${query}" : ""} for tenant ${tenant.tenantId}...")

    Map<String, String> headers = getTenantHttpHeaders(tenant)

    String url = generateUrl("/users${query ? "?query=${query}&limit=${limit}" : "?limit=${limit}"}")

    Map response = restClient.get(url, headers).body as Map

    if (response.totalRecords > 0) {
      logger.debug("Found users: ${response.users}")
      List<User> users = []
      response.users.each { userContent ->
        users.add(User.getUserFromContent(userContent as Map, tenant, UserGroups.get(this)))
      }
      return users
    } else {
      logger.debug("By the url ${url} user(s) not found")
      logger.debug("HTTP response is: ${response}")
      throw new Exception("User(s) not found")
    }
  }

  Users setUpdatePassword(EurekaTenant tenant, User user){
    logger.info("Setting or updating user password for user ${user.username}(${user.uuid}) for ${tenant.tenantId}...")

    Map<String, String> headers = getTenantHttpHeaders(tenant)

    Map body = [
      "username": user.username,
      "userId"  : user.uuid,
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

      unsetPassword(tenant, user).setUpdatePassword(tenant, user)
    }

    return this
  }

  Users unsetPassword(EurekaTenant tenant, User user){
    logger.info("Unset user password for user ${user.username}(${user.uuid}) for ${tenant.tenantId}...")

    Map<String, String> headers = getTenantHttpHeaders(tenant)

    restClient.delete(generateUrl("/authn/credentials?userId=${user.uuid}"), headers)

    return this
  }

  Users getAndAssignSPs(EurekaTenant tenant, User user) {

    logger.info("Retreiving and assigning service points for user ${user.username}(${user.uuid}) for ${tenant.tenantId}...")

    String url = generateUrl("/service-points")

    Map<String, String> headers = getTenantHttpHeaders(tenant)

    List servicePointsIds = restClient.get(url, headers).body.servicepoints*.id

    Map body = [userId               : user.uuid,
                servicePointsIds     : servicePointsIds,
                defaultServicePointId: servicePointsIds.first()]

    restClient.post(generateUrl("/service-points-users"), body, headers, [201, 400, 422])

    logger.info("Service points: ${servicePointsIds.join(", ")} successfully assigned to user ${user.username}(${user.uuid})")

    return this

  }

  Users invokeUsersMigration(EurekaTenant tenant) {

    logger.info("Invoking users migration for ${tenant.tenantId}...")

    Map<String, String> headers = getTenantHttpHeaders(tenant)

    restClient.post(generateUrl("/users-keycloak/migrations"), "",headers)

    sleep(30)

    return this
  }

  @NonCPS
  static Users get(Kong kong){
    return new Users(kong)
  }
}
