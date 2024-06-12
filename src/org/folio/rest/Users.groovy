package org.folio.rest

import groovy.json.JsonOutput
import hudson.AbortException
import org.folio.rest.model.GeneralParameters
import org.folio.rest.model.OkapiTenant
import org.folio.rest.model.OkapiUser

class Users extends GeneralParameters {

  private Authorization auth = new Authorization(steps, okapi_url)

  Users(Object steps, String okapi_url) {
    super(steps, okapi_url)
  }

  /**
   * Validate if user object has required fields
   * @param user
   */
  static void validateUser(OkapiUser user) {
    if (!user.password) {
      throw new Exception(user.username + ' password does not specified')
    } else if (!user.permissions) {
      throw new Exception('Permissions for ' + user.username + ' does not specified')
    } else if (!user.uuid) {
      throw new Exception(user.username + ' uuid does not specified')
    }
  }

  /**
   * Get User by username
   * @param tenant
   * @param user
   * @return
   */
  def getUser(OkapiTenant tenant, OkapiUser user) {
    auth.getOkapiToken(tenant, tenant.getAdminUser())
    String url = okapi_url + "/users?query=username%3d%3d" + user.username
    ArrayList headers = [[name: 'X-Okapi-Tenant', value: tenant.getId()],
                         [name: 'X-Okapi-Token', value: tenant.getAdminUser().getToken() ? tenant.getAdminUser().getToken() : '', maskValue: true]]
    def res = http.getRequest(url, headers)
    if (res.status == HttpURLConnection.HTTP_OK) {
      return tools.jsonParse(res.content)
    } else {
      throw new AbortException("Can not get user details." + http.buildHttpErrorMessage(res))
    }
  }

  /**
   * Create user
   * @param tenant
   * @param user
   * @return
   */
  void createUser(OkapiTenant tenant, OkapiUser user) {
    auth.getOkapiToken(tenant, tenant.getAdminUser())
    String url = okapi_url + "/users"
    ArrayList headers = [[name: 'Content-type', value: "application/json"],
                         [name: 'X-Okapi-Tenant', value: tenant.getId()],
                         [name: 'X-Okapi-Token', value: tenant.getAdminUser().getToken() ? tenant.getAdminUser().getToken() : '', maskValue: true]]
    def checkUser = getUser(tenant, user)
    if (checkUser.totalRecords.toInteger() == 0) {
      logger.info("User ${user.username} does not exists. Creating...")
      String uuid = UUID.randomUUID().toString()
      String body = JsonOutput.toJson([id      : uuid,
                                       username: user.username,
                                       barcode : user.barcode,
                                       active  : true,
                                       personal: [lastName : user.lastName,
                                                  firstName: user.firstName,
                                                  email    : user.email]])
      def res = http.postRequest(url, body, headers)
      if (res.status == HttpURLConnection.HTTP_CREATED) {
        logger.info("User ${user.username} successfully created")
        user.setUuid(uuid)
      } else {
        throw new AbortException("Can not create user ${user.username}." + http.buildHttpErrorMessage(res))
      }
    } else if (checkUser.totalRecords.toInteger() > 0) {
      logger.info("User ${user.username} already exists. UUID: ${checkUser.users[0].id}")
      user.setUuid(checkUser.users[0].id)
    }
  }

  /**
   * Set patron group for user
   * @param tenant
   * @param user
   * @param patronGroupId
   */
  //TODO Configure to edit user
  void setPatronGroup(OkapiTenant tenant, OkapiUser user, String patronGroupId) {
    auth.getOkapiToken(tenant, tenant.getAdminUser())
    String url = okapi_url + "/users/" + user.uuid
    ArrayList headers = [[name: 'Content-type', value: "application/json"],
                         [name: 'X-Okapi-Tenant', value: tenant.getId()],
                         [name: 'X-Okapi-Token', value: tenant.getAdminUser().getToken() ? tenant.getAdminUser().getToken() : '', maskValue: true]]
    logger.info("Assign patron group ${user.groupName} with id ${patronGroupId} for user ${user.username}")
    String body = JsonOutput.toJson(getUser(tenant, user).users[0] << [patronGroup: patronGroupId])
    def res = http.putRequest(url, body, headers)
    if (res.status == HttpURLConnection.HTTP_NO_CONTENT) {
      logger.info("Patron group ${user.groupName} with id ${patronGroupId} assigned for user ${user.username}")
    } else {
      throw new AbortException("Can not assign patron group ${user.groupName} for user ${user.username}" + http.buildHttpErrorMessage(res))
    }
  }

  /**
   * Get user patron group Id
   * @param tenant
   * @param user
   */
  def getPatronGroupId(OkapiTenant tenant, OkapiUser user) {
    auth.getOkapiToken(tenant, tenant.getAdminUser())
    String url = okapi_url + "/groups"
    ArrayList headers = [[name: 'X-Okapi-Tenant', value: tenant.getId()],
                         [name: 'X-Okapi-Token', value: tenant.getAdminUser().getToken() ? tenant.getAdminUser().getToken() : '', maskValue: true]]
    def res = http.getRequest(url, headers)
    if (res.status == HttpURLConnection.HTTP_OK) {
      return tools.jsonParse(res.content).usergroups.findResult { if (it.group == user.groupName) return it.id }
    } else {
      throw new AbortException("Can not get patron groups." + http.buildHttpErrorMessage(res))
    }
  }

  void resetUserPassword(OkapiTenant tenant, OkapiUser user) {
    if (!user.uuid) {
      def checkUser = getUser(tenant, user)
      user.setUuid(checkUser.users[0].id)
    }

    auth.getOkapiToken(tenant, tenant.getAdminUser())
    String passResetActionUrl = okapi_url + "/authn/password-reset-action"
    String resetPassUrl = okapi_url + "/authn/reset-password"
    String passwordResetActionId = UUID.randomUUID().toString()
    ArrayList headers = [
      [name: 'Content-type', value: "application/json"],
      [name: 'X-Okapi-Tenant', value: tenant.getId()],
      [name: 'X-Okapi-Token', value: tenant.getAdminUser().getToken() ? tenant.getAdminUser().getToken() : '', maskValue: true]
    ]

    String passResetActionBody = JsonOutput.toJson([userId        : user.uuid,
                                                    id            : passwordResetActionId,
                                                    expirationTime: 200])
    String resetPassBody = JsonOutput.toJson([passwordResetActionId: passwordResetActionId,
                                              newPassword          : user.password])

    logger.info("Reseting password for ${user.username} user...")
    // Set password rest action ID
    http.postRequest(passResetActionUrl, passResetActionBody, headers)

    logger.info("Changing password for ${user.username} user...")
    def res = http.postRequest(resetPassUrl, resetPassBody, headers)
    if (res.status == HttpURLConnection.HTTP_CREATED) {
      logger.info("${user.username} password successfully changed")
    } else {
      throw new AbortException("Password for ${user.username} user not changed." + http.buildHttpErrorMessage(res))
    }
  }
}
