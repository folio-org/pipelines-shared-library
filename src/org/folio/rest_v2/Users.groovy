package org.folio.rest_v2

import org.folio.models.OkapiTenant
import org.folio.models.OkapiUser
import org.folio.utilities.RequestException

/**
 * The Users class extends the Authorization class.
 * This class is responsible for managing users in the system, including their service points and password.
 */
class Users extends Authorization {

    /**
     * Initializes a new instance of the Users class.
     *
     * @param context The current context.
     * @param okapiDomain The domain for Okapi.
     * @param debug Debug flag indicating whether debugging is enabled.
     */
    Users(Object context, String okapiDomain, boolean debug = false) {
        super(context, okapiDomain, debug)
    }

    /**
     * Retrieves a user by their username.
     *
     * @param tenant The tenant in which to look for the user.
     * @param user The user to be retrieved.
     * @return The retrieved user.
     */
    Map getUserByName(OkapiTenant tenant, OkapiUser user) {
        String url = generateUrl("/users?query=username%3d%3d${user.username}")
        Map<String, String> headers = getAuthorizedHeaders(tenant)

        try {
            Map response = restClient.get(url, headers).body
            if (response.totalRecords.toInteger() > 0) {
                Map userObject = response.users.find { it.username == user.username }
                user.setUuid(userObject.id)
                return userObject
            }
        }
        catch (RequestException e) {
            logger.error("Not able to get user details: ${e.getMessage()}")
            throw e
        }
    }

    /**
     * Creates a new user.
     *
     * @param tenant The tenant in which to create the user.
     * @param user The user to be created.
     */
    void createUser(OkapiTenant tenant, OkapiUser user) {
        if (getUserByName(tenant, user)) {
            logger.info("User ${user.username} already exists. UUID: ${user.uuid}")
            return
        }

        String url = generateUrl("/users")
        Map<String, String> headers = getAuthorizedHeaders(tenant)

        logger.info("User ${user.username} does not exists. Creating...")
        user.generateUserUuid()
        Map body = [id      : user.uuid,
                    username: user.username,
                    barcode : user.barcode,
                    type    : user.type,
                    active  : true,
                    personal: [lastName : user.lastName,
                               firstName: user.firstName,
                               email    : user.email]]
        try {
            restClient.post(url, body, headers)
            logger.info("User ${user.username} created successfully")
        } catch (RequestException e) {
            throw new RequestException("Can not create user: ${user.username}. ${e.getMessage()}", e.statusCode)
        }
    }

  /**
   * Creates a new user.
   *
   * @param tenant The tenant in which to create the user.
   * @param user The user to be created.
   */
  void deleteUser(OkapiTenant tenant, OkapiUser user) {
    if (!getUserByName(tenant, user)) {
      logger.info("User ${user.username} not exists.")
      return
    }

    String url = generateUrl("/users")
    Map<String, String> headers = getAuthorizedHeaders(tenant)

    logger.info("User ${user.username} exists. Deleting...")
    user.generateUserUuid()
    try {
      restClient.delete(url, headers)
      logger.info("User ${user.username} deleted successfully")
    } catch (RequestException e) {
      throw new RequestException("Can not delete user: ${user.username}. ${e.getMessage()}", e.statusCode)
    }
  }

    /**
     * Retrieves the service points IDs for a tenant.
     *
     * @param tenant The tenant for which to retrieve the service points IDs.
     * @return The list of service points IDs.
     */
    List getServicePointsIds(OkapiTenant tenant) {
        String url = generateUrl("/service-points")
        Map<String, String> headers = getAuthorizedHeaders(tenant)

        return restClient.get(url, headers).body.servicepoints*.id
    }

    /**
     * Checks if a user has service points records.
     *
     * @param tenant The tenant in which to look for the user.
     * @param user The user to be checked.
     * @return A boolean indicating whether the user has service points records.
     */
    def checkUserHasServicePointsRecords(OkapiTenant tenant, OkapiUser user) {
        user.checkUuid()

        String url = generateUrl("/service-points-users?query=userId%3d%3d${user.uuid}")
        Map<String, String> headers = getAuthorizedHeaders(tenant)

        def response = restClient.get(url, headers).body
        return response.totalRecords.toInteger() > 0
    }

    /**
     * Creates a service points user record for a user.
     *
     * @param tenant The tenant in which to create the record.
     * @param user The user for whom to create the record.
     * @param servicePointsIds The service points IDs to associate with the user.
     */
    void createServicePointsUserRecord(OkapiTenant tenant, OkapiUser user, List servicePointsIds) {
        if (servicePointsIds.isEmpty()) {
            logger.warning("Service points ids list is empty")
            return
        }

        user.checkUuid()

        String url = generateUrl("/service-points-users")
        Map<String, String> headers = getAuthorizedHeaders(tenant)

        try {
            Map body = [userId               : user.uuid,
                        servicePointsIds     : servicePointsIds,
                        defaultServicePointId: servicePointsIds.first()]
            logger.info("Assign service points (${servicePointsIds.join(", ")}) to user ${user.username}")
            restClient.post(url, body, headers)
            logger.info("Service points (${servicePointsIds.join(", ")}) successfully assigned to user ${user.username}")
        } catch (RequestException e) {
            if (e.statusCode == 422) {
                logger.warning("Unable to proceed request: ${e.getMessage()}")
            } else {
                throw new RequestException("Unable to proceed request: ${e.getMessage()}", e.statusCode)
            }
        }
    }

    /**
     * Retrieves the patron group ID for a group name.
     *
     * @param tenant The tenant in which to look for the group.
     * @param groupName The group name.
     * @return The patron group ID.
     */
    String getPatronGroupId(OkapiTenant tenant, String groupName) {
        String url = generateUrl("/groups")
        Map<String, String> headers = getAuthorizedHeaders(tenant)

        def response = restClient.get(url, headers).body
        return response.usergroups.findResult { if (it.group == groupName) return it.id }
    }

    /**
     * Sets the patron group for a user.
     *
     * @param tenant The tenant in which the user resides.
     * @param user The user for whom to set the patron group.
     */
    void setPatronGroup(OkapiTenant tenant, OkapiUser user) {
        if (!user.group) {
            logger.warning("Patron group not set for user ${user.username}.")
            return
        }
        user.checkUuid()

        logger.info("Assign patron group ${user.group} for user ${user.username}")
        String url = generateUrl("/users/${user.uuid}")
        Map<String, String> headers = getAuthorizedHeaders(tenant)
        Map body = getUserByName(tenant, user)
        body["patronGroup"] = getPatronGroupId(tenant, user.group)

        restClient.put(url, body, headers)
        logger.info("Patron group ${user.group} with id ${body["patronGroup"]} assigned for user ${user.username}")
    }

    /**
     * Triggers the action of resetting the password for a user.
     *
     * @param tenant The tenant in which the user resides.
     * @param user The user whose password is to be reset.
     * @return The ID of the reset password action.
     */
    String resetPasswordAction(OkapiTenant tenant, OkapiUser user) {
        user.checkUuid()

        String url = generateUrl("/authn/password-reset-action")
        Map<String, String> headers = getAuthorizedHeaders(tenant)
        Map body = [userId        : user.uuid,
                    id            : UUID.randomUUID().toString(),
                    expirationTime: 200]

        logger.info("Reseting password for ${user.username} user...")
        restClient.post(url, body, headers)
        return body["id"]
    }

    /**
     * Resets the password for a user.
     *
     * @param tenant The tenant in which the user resides.
     * @param user The user whose password is to be reset.
     */
    void resetUserPassword(OkapiTenant tenant, OkapiUser user) {
        user.checkUuid()
        String url = generateUrl("/authn/reset-password")
        Map<String, String> headers = getAuthorizedHeaders(tenant)
        Map body = [passwordResetActionId: resetPasswordAction(tenant, user),
                    newPassword          : user.password]

        logger.info("Changing password for ${user.username} user...")
        restClient.post(url, body, headers)
        logger.info("${user.username} password successfully changed")
    }
}
