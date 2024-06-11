package org.folio.rest

import groovy.json.JsonOutput
import hudson.AbortException
import org.folio.rest.model.GeneralParameters
import org.folio.rest.model.OkapiTenant
import org.folio.rest.model.OkapiUser

class Authorization extends GeneralParameters {

  Authorization(Object steps, String okapi_url) {
    super(steps, okapi_url)
  }

  /**
   * Get information about user credentials
   * @param tenant
   * @param user
   * @return
   */
  def getUserCredentials(OkapiTenant tenant, OkapiUser user) {
    if (!user.uuid) {
      throw new AbortException("${user.username} uuid does not specified")
    }
    getOkapiToken(tenant, tenant.getAdminUser())
    String url = okapi_url + "/authn/credentials-existence?userId=" + user.uuid
    ArrayList headers = [
      [name: 'X-Okapi-Tenant', value: tenant.getId()],
      [name: 'X-Okapi-Token', value: tenant.getAdminUser().getToken() ? tenant.getAdminUser().getToken() : '', maskValue: true]
    ]
    def res = http.getRequest(url, headers)
    if (res.status == HttpURLConnection.HTTP_OK) {
      return tools.jsonParse(res.content)
    } else {
      throw new AbortException("Can not get user credentials." + http.buildHttpErrorMessage(res))
    }
  }

  /**
   * Create credentials entry for user
   * @param tenant
   * @param user
   */
  void createUserCredentials(OkapiTenant tenant, OkapiUser user) {
    if (!user.uuid) {
      throw new AbortException("${user.username} uuid does not specified")
    }
    getOkapiToken(tenant, tenant.getAdminUser())
    String url = okapi_url + "/authn/credentials"
    ArrayList headers = [
      [name: 'Content-type', value: "application/json"],
      [name: 'X-Okapi-Tenant', value: tenant.getId()],
      [name: 'X-Okapi-Token', value: tenant.getAdminUser().getToken() ? tenant.getAdminUser().getToken() : '', maskValue: true]
    ]
    if (!getUserCredentials(tenant, user).credentialsExist) {
      logger.info("${user.username} does not have credentials. Creating...")
      String body = JsonOutput.toJson([userId  : user.uuid,
                                       password: user.password])
      def res = http.postRequest(url, body, headers)
      if (res.status == HttpURLConnection.HTTP_CREATED) {
        logger.info("${user.username} credentials successfully set")
      } else {
        throw new AbortException("Can not set credentials for ${user.username}." + http.buildHttpErrorMessage(res))
      }
    } else {
      logger.info("${user.username} credentials already set")
    }
  }

  /**
   * Login via bl-users
   * @param tenant
   * @param user
   * @return
   */
  void login(OkapiTenant tenant, OkapiUser user) {
    String url = okapi_url + "/bl-users/login"
    ArrayList headers = [
      [name: "Content-type", value: "application/json"],
      [name: "X-Okapi-Tenant", value: tenant.getId()],
    ]
    logger.info('Login as user: ' + user.username)
    String body = JsonOutput.toJson([username: user.username,
                                     password: user.password])
    def res = http.postRequest(url, body, headers)
    if (res.status == HttpURLConnection.HTTP_CREATED) {
      user.setToken(res.headers['x-okapi-token'])
      user.setUuid(tools.jsonParse(res.content).user.id)
      user.setPermissions(tools.jsonParse(res.content).permissions.permissions)
      user.setPermissionsId(tools.jsonParse(res.content).permissions.id)
    } else {
      throw new AbortException("Unable to login as user:  ${user.username}" + http.buildHttpErrorMessage(res))
    }
  }

  /**
   * Get Okapi token
   * @param tenant
   * @param user
   * @return
   */
  void getOkapiToken(OkapiTenant tenant, OkapiUser user) {
    String url = okapi_url + "/authn/login"
    ArrayList headers = [
      [name: "Content-type", value: "application/json"],
      [name: "X-Okapi-Tenant", value: tenant.getId()],
    ]
    String body = JsonOutput.toJson([username: user.username,
                                     password: user.password])
    def res = http.postRequest(url, body, headers)
    if (res.status == HttpURLConnection.HTTP_CREATED) {
      user.setToken(tools.jsonParse(res.content).okapiToken)
    } else if (HttpURLConnection.HTTP_BAD_REQUEST || res.status == HttpURLConnection.HTTP_NOT_FOUND || res.status == 422) {
      user.setToken('')
    } else {
      throw new AbortException("Unable to get token for user: " + user.username + http.buildHttpErrorMessage(res))
    }
  }

  void adminUserLogin(OkapiTenant tenant, OkapiUser admin_user) {
    admin_user.setToken(getOkapiToken(tenant, admin_user))
    tenant.setAdminUser(admin_user)
  }
}
