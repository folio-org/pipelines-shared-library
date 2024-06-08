package org.folio.rest_v2

import groovy.json.JsonOutput
import org.folio.models.OkapiUser
import org.folio.models.OkapiTenant
import org.folio.utilities.RequestException

/**
 * Permissions is a class that extends the Authorization class.
 * It is responsible for performing operations related to permissions such as fetching,
 * assigning, and creating permissions for users and tenants in the system.
 */
class Permissions extends Authorization {

  /**
   * Initializes a new instance of the Permissions class.
   *
   * @param context The current context.
   * @param okapiDomain The domain for Okapi.
   * @param debug Debug flag indicating whether debugging is enabled.
   */
  Permissions(Object context, String okapiDomain, boolean debug = false) {
    super(context, okapiDomain, debug)
  }

  /**
   * Retrieves a list of all permissions for a tenant, excluding certain specific types.
   *
   * @param tenant The tenant for which permissions are fetched.
   * @return A list of all permissions for the tenant.
   */
  List getAllPermissions(OkapiTenant tenant) {
    String url = generateUrl("/perms/permissions?query=cql.allRecords%3D1%20" +
      "not%20permissionName%3D%3Dokapi.%2A%20" +
      "not%20permissionName%3D%3Dperms.users.assign.okapi%20" +
      "not%20permissionName%3D%3Dmodperms.%2A%20" +
      "not%20permissionName%3D%3DSYS%23%2A&length=5000")
    Map<String, String> headers = getAuthorizedHeaders(tenant)

    logger.info("Get all permissions list. Except okapi.*, modperms.* and SYS#*")

    def response = restClient.get(url, headers).body

    if (response.permissions.size() == response.totalRecords.toInteger()) {
      List permissions = []
      response.permissions.each {
        if (it.childOf.count { it.startsWith('SYS#') } == it.childOf.size()) {
          permissions.add(it.permissionName)
        }
      }
      logger.info("Retrieved: ${permissions.size()} permissions")
      return permissions
    } else {
      logger.error("Retrieved permissions ${response.permissions.size()} don't match total permissions count ${response.totalRecords}")
    }
  }

  /**
   * Retrieves a list of all permissions for a user.
   *
   * @param tenant The tenant in which the user is located.
   * @param user The user for which permissions are fetched.
   * @return A list of all permissions for the user.
   */
  List getUserPermissions(OkapiTenant tenant, OkapiUser user) {
    user.checkUuid()

    String url = generateUrl("/perms/users?query=userId%3d%3d${user.uuid}")
    Map<String, String> headers = getAuthorizedHeaders(tenant)

    Map response = restClient.get(url, headers).body

    if (response.totalRecords.toInteger() > 0) {
      Map permissionsObject = response.permissionUsers.find { it.userId == user.uuid }
      user.setPermissionsId(permissionsObject.id)
      return permissionsObject.permissions
    }
  }

  /**
   * Creates permissions for a user.
   *
   * @param tenant The tenant in which the user is located.
   * @param user The user for which permissions are created.
   */
  void createUserPermissions(OkapiTenant tenant, OkapiUser user) {
    user.checkUuid()

    String url = generateUrl("/perms/users")
    Map<String, String> headers = getAuthorizedHeaders(tenant)
    Map body = [userId     : user.uuid,
                permissions: user.permissions]
    try {
      logger.info("Creating permissions for ${user.username}")

      def response = restClient.post(url, body, headers).body
      user.setPermissionsId(response.id)

      logger.info(JsonOutput.prettyPrint(JsonOutput.toJson(response.permissions)))
    } catch (RequestException e) {
      if (e.statusCode == HttpURLConnection.HTTP_BAD_REQUEST) {
        if (getUserPermissions(tenant, user)) {
          logger.warning("Seems that user permissions have already created. Permissions Id: ${user.permissionsId}")
        } else {
          logger.error("Failed to get permissions for user: ${user.username}. ${e.getMessage()}")
        }
      } else {
        logger.error("Failed to create permissions for user: ${user.username}. ${e.getMessage()}")
      }
    }

  }

  /**
   * Adds a list of permissions to a user.
   *
   * @param tenant The tenant in which the user is located.
   * @param user The user to which permissions are added.
   * @param permissions The list of permissions to add.
   */
  void addUserPermissions(OkapiTenant tenant, OkapiUser user, List permissions) {
    if (permissions.isEmpty()) {
      logger.warning("Permissions list is empty")
      return
    }

    user.permissions = getUserPermissions(tenant, user)
    user.checkPermissionsId()

    String url = generateUrl("/perms/users/${user.permissionsId}")
    Map<String, String> headers = getAuthorizedHeaders(tenant)
    Map body = [userId     : user.uuid,
                permissions: (user.permissions + permissions).toSet().toList()]

    restClient.put(url, body, headers)

    logger.info("All permissions successfully assigned to user ${user.username}")
    logger.info("Wait a minute for permissions cache update")
    sleep(60000)
  }

  /**
   * Adds a specific permission to a user.
   *
   * @param tenant The tenant in which the user is located.
   * @param user The user to which the permission is added.
   * @param permissionName The permission to add.
   */
  void addUserPermission(OkapiTenant tenant, OkapiUser user, String permissionName) {
    user.permissions = getUserPermissions(tenant, user)
    user.checkPermissionsId()

    if (user.permissions.contains(permissionName)) {
      logger.warning("User ${user.username} already has ${permissionName} permission")
      return
    }

    String url = generateUrl("/perms/users/${user.permissionsId}/permissions")
    Map<String, String> headers = getAuthorizedHeaders(tenant)

    logger.info("Assigning permission ${permissionName} to user ${user.username}")

    restClient.post(url, [permissionName: permissionName], headers)

    logger.info("Permission ${permissionName} successfully assigned to user ${user.username}")
    logger.info("Wait a minute for permissions cache update")
    sleep(60000)
  }

//TODO: Params will be defined here later
  void deleteUserPermission(OkapiTenant tenant, OkapiUser user, String permissionName) {

    String url = generateUrl("/perms/users/${user.permissionsId}/permissions/${permissionName}")
    Map<String, String> headers = getAuthorizedHeaders(tenant)

    logger.info("Deleting permission ${permissionName} of user ${user.username}")

    restClient.delete(url, headers)

    logger.info("Permission ${permissionName} of user ${user.username}successfully deleted")
    logger.info("Wait a minute for permissions cache update")
    sleep(60000)
  }

  /**
   * Adds a missing permission to a user.
   * source: RANCHER-895
   * @param tenant The tenant in which the user is located.
   * @param user The user to whom the permission is added.
   */
  void refreshAdminPermissions(OkapiTenant tenant, OkapiUser user) {
    List allPermissions = getAllPermissions(tenant)
    List existingPermissions = getUserPermissions(tenant, user)
    List permissionsToAdd = []
    allPermissions.each { permissionName ->
      if (!existingPermissions.contains(permissionName)) {
        permissionsToAdd.add(permissionName)
      }
    }
    try {
      addUserPermissions(tenant, user, permissionsToAdd)
      logger.info("${permissionsToAdd.size()} permissions added to user ${user.getUsername()}")
    }
    catch (e) {
      logger.warning("Add permission operation failed with error: ${e.getMessage()}")
    }
  }

  /**
   * Purges deprecated permissions for a given Okapi tenant.
   *
   * Sends a POST request to "/perms/purge-deprecated" to clear deprecated permissions
   * and logs the names of the purged permissions.
   *
   * @param tenant The Okapi tenant whose deprecated permissions are to be purged.
   */
  void purgeDeprecatedPermissions(OkapiTenant tenant) {
    String url = generateUrl("/perms/purge-deprecated")
    Map<String, String> headers = getAuthorizedHeaders(tenant)

    Map response = restClient.post(url, null, headers).body

    logger.info("Purged permissions: ${response.permissionNames.size()}")
  }
}
