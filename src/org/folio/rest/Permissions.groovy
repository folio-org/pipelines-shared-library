package org.folio.rest

import groovy.json.JsonOutput
import hudson.AbortException
import org.folio.rest.model.GeneralParameters
import org.folio.rest.model.OkapiTenant
import org.folio.rest.model.OkapiUser

class Permissions extends GeneralParameters {

  private Users users = new Users(steps, okapi_url)

  private Authorization auth = new Authorization(steps, okapi_url)

  Permissions(Object steps, String okapi_url) {
    super(steps, okapi_url)
  }

  /**
   * Get All available permissions in tenant
   * cql query for cql.allRecords=1 not permissionName==okapi.* not permissionName==modperms.* not permissionName==SYS#*
   * @param tenant
   * @param user
   */
  def getAllPermissions(OkapiTenant tenant) {
    auth.getOkapiToken(tenant, tenant.getAdminUser())
    ArrayList permissions = []
    String url = okapi_url + "/perms/permissions?query=cql.allRecords%3D1%20not%20permissionName%3D%3Dokapi.%2A%20not%20permissionName%3D%3Dperms.users.assign.okapi%20not%20permissionName%3D%3Dmodperms.%2A%20not%20permissionName%3D%3DSYS%23%2A&length=5000"
    ArrayList headers = [[name: 'X-Okapi-Tenant', value: tenant.getId()],
                         [name: 'X-Okapi-Token', value: tenant.getAdminUser().getToken() ? tenant.getAdminUser().getToken() : '', maskValue: true]]
    logger.info("Get all permissions list. Except okapi.*, modperms.* and SYS#*")
    def res = http.getRequest(url, headers)
    if (res.status == HttpURLConnection.HTTP_OK) {
      def response = tools.jsonParse(res.content)
      if (response.permissions.size() == response.totalRecords.toInteger()) {
        response.permissions.each {
          if (it.childOf.count { it.startsWith('SYS#') } == it.childOf.size()) {
            permissions.add(it.permissionName)
          }
        }
        return permissions
      } else {
        throw new AbortException("Retrieved permissions ${response.permissions.size()} don't match total permissions count ${response.totalRecords}")
      }
    } else {
      throw new AbortException("Can not get all permissions list." + http.buildHttpErrorMessage(res))
    }
  }

  /**
   * Get User permissions
   * @param tenant
   * @param user
   * @return
   */
  def getUserPermissions(OkapiTenant tenant, OkapiUser user) {
    users.validateUser(user)
    auth.getOkapiToken(tenant, tenant.getAdminUser())
    String url = okapi_url + "/perms/users?query=userId%3d%3d" + user.uuid
    ArrayList headers = [[name: 'X-Okapi-Tenant', value: tenant.getId()],
                         [name: 'X-Okapi-Token', value: tenant.getAdminUser().getToken() ? tenant.getAdminUser().getToken() : '', maskValue: true]]
    def res = http.getRequest(url, headers)
    if (res.status == HttpURLConnection.HTTP_OK) {
      return tools.jsonParse(res.content)
    } else {
      throw new Exception("Can not get user permissions." + http.buildHttpErrorMessage(res))
    }
  }

  /**
   * Create permissions for user
   * @param tenant
   * @param user
   */
  void createUserPermissions(OkapiTenant tenant, OkapiUser user) {
    users.validateUser(user)
    auth.getOkapiToken(tenant, tenant.getAdminUser())
    String url = okapi_url + "/perms/users"
    ArrayList headers = [[name: 'Content-type', value: "application/json"],
                         [name: 'X-Okapi-Tenant', value: tenant.getId()],
                         [name: 'X-Okapi-Token', value: tenant.getAdminUser().getToken() ? tenant.getAdminUser().getToken() : '', maskValue: true]]
    ArrayList permissionsDiff = []
    def checkUserPermissions = getUserPermissions(tenant, user)
    if (checkUserPermissions.totalRecords.toInteger() > 0) {
      user.permissions.each {
        if (!checkUserPermissions.permissionUsers[0].permissions.contains(it)) {
          permissionsDiff.add(it)
        }
      }
    }
    if (checkUserPermissions.totalRecords.toInteger() == 0) {
      logger.info("${user.username} does not have any permissions. Creating...")
      String body = JsonOutput.toJson([userId     : user.uuid,
                                       permissions: user.permissions])
      def res = http.postRequest(url, body, headers)
      if (res.status == HttpURLConnection.HTTP_CREATED) {
        logger.info("${user.username} permissions ${user.permissions} successfully assigned")
      } else {
        throw new AbortException("Can not set permissions for user ${user.username}." + http.buildHttpErrorMessage(res))
      }
    } else if (checkUserPermissions.totalRecords.toInteger() > 0 && !permissionsDiff) {
      logger.info("${user.username} already have required permissions: ${user.permissions.join(", ")}")
    } else {
      //TODO Investigate how it is possible to handle if user already have permissions but it differ from proposed
      logger.warning("=(")
    }
  }
  /**
   * Assign permissions for user
   * @param tenant
   * @param user
   * @param permissions
   */
  void assignUserPermissions(OkapiTenant tenant, OkapiUser user, ArrayList permissions) {
    auth.getOkapiToken(tenant, tenant.getAdminUser())
    String url = okapi_url + "/perms/users/" + user.permissionsId + "/permissions"
    ArrayList headers = [[name: 'Content-type', value: "application/json"],
                         [name: 'X-Okapi-Tenant', value: tenant.getId()],
                         [name: 'X-Okapi-Token', value: tenant.getAdminUser().getToken() ? tenant.getAdminUser().getToken() : '', maskValue: true]]
    if (permissions) {
      logger.info("Assign permissions to ${user.username}")
      Integer count = 0
      permissions.each {
        if (!user.permissions.contains(it)) {
          String body = JsonOutput.toJson([permissionName: it])
          def res = http.postRequest(url, body, headers)
          if (res.status == HttpURLConnection.HTTP_OK) {
            logger.info(body)
            count++
          } else {
            throw new AbortException("Can not able to add permission: ${it}." + http.buildHttpErrorMessage(res))
          }
        }
      }
      if (count > 0) {
        logger.info("Wait a minute for permissions cache update")
        sleep(60000)
      }
    } else {
      logger.warning("Permissions list fetched from okapi is empty")
    }
  }
}
