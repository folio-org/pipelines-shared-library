package org.folio.rest

import groovy.json.JsonOutput
import org.folio.http.HttpClient
import org.folio.rest.model.OkapiTenant
import org.folio.rest.model.OkapiUser
import org.folio.utilities.Logger
import org.folio.utilities.Tools

class Permissions implements Serializable {
    def steps
    private String okapiUrl = 'localhost:9130'
    private LinkedHashMap headers = ['Content-Type': 'application/json']

    private Users users = new Users(steps, okapiUrl)

    private Tools tools = new Tools()
    private HttpClient http = new HttpClient()
    private Logger logger = new Logger(steps, this.getClass().getCanonicalName())

    Permissions(steps, okapiUrl) {
        this.steps = steps
        this.okapiUrl = okapiUrl
    }

    /**
     * Get All available permissions in tenant
     * cql query for cql.allRecords=1 not permissionName==okapi.* not permissionName==modperms.* not permissionName==SYS#*
     * @param tenant
     * @param user
     */
    def getAllPermissions(OkapiTenant tenant) {
        ArrayList permissions = []
        String uri = '/perms/permissions?query=cql.allRecords%3D1%20not%20permissionName%3D%3Dokapi.%2A%20not%20permissionName%3D%3Dperms.users.assign.okapi%20not%20permissionName%3D%3Dmodperms.%2A%20not%20permissionName%3D%3DSYS%23%2A&length=5000'
        this.headers['X-Okapi-Tenant'] = tenant.getId()
        this.headers['X-Okapi-Token'] = tenant.getAdmin_user().getToken() ? tenant.getAdmin_user().getToken() : ''
        logger.info('Get all permissions list. Except okapi.*, modperms.* and SYS#*')
        def res = http.request(method: 'GET', url: okapiUrl, uri: uri, headers: headers)
        if (res['status_code'].toInteger() == 200) {
            def response = tools.jsonParse(res['response'])
            if (response.permissions.size() == response.totalRecords.toInteger()) {
                response.permissions.each {
                    if (it.childOf.count { it.startsWith('SYS#') } == it.childOf.size()) {
                        permissions.add(it.permissionName)
                    }
                }
                return permissions
            } else {
                throw new Exception('Retrieved permissions ' + response.permissions.size() + ' don\'t match total permissions count ' + response.totalRecords)
            }
        } else {
            throw new Exception('Can not get all permissions list. Status code:' + res['status_code'])
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
        String uri = '/perms/users?query=userId%3d%3d' + user.uuid
        this.headers['X-Okapi-Tenant'] = tenant.getId()
        this.headers['X-Okapi-Token'] = tenant.getAdmin_user().getToken() ? tenant.getAdmin_user().getToken() : ''
        def res = http.request(method: 'GET', url: okapiUrl, uri: uri, headers: headers)
        if (res['status_code'].toInteger() == 200) {
            return tools.jsonParse(res['response'])
        } else {
            throw new Exception('Can not get user permissions. Status code:' + res['status_code'])
        }
    }

    /**
     * Create permissions for user
     * @param tenant
     * @param user
     */
    void createUserPermissions(OkapiTenant tenant, OkapiUser user) {
        users.validateUser(user)
        String uri = '/perms/users'
        this.headers['X-Okapi-Tenant'] = tenant.getId()
        this.headers['X-Okapi-Token'] = tenant.getAdmin_user().getToken() ? tenant.getAdmin_user().getToken() : ''
        def checkUserPermissions = getUserPermissions(tenant, user)
        ArrayList permissionsDiff = []
        if (checkUserPermissions.totalRecords.toInteger() > 0) {
            user.permissions.each {
                if (!checkUserPermissions.permissionUsers[0].permissions.contains(it)) {
                    permissionsDiff.add(it)
                }
            }
        }
        if (checkUserPermissions.totalRecords.toInteger() == 0) {
            logger.info(user.username + ' does not have any permissions. Creating...')
            String json = JsonOutput.toJson([userId     : user.uuid,
                                             permissions: user.permissions])
            def res = http.request(method: 'POST', url: okapiUrl, uri: uri, headers: headers, body: json)
            if (res['status_code'].toInteger() == 201) {
                logger.info(user.username + ' permissions successfully assigned')
            } else {
                throw new Exception('Can not set permissions for user ' + user.username + '. Status code:' + res['status_code'])
            }
        } else if (checkUserPermissions.totalRecords.toInteger() > 0 && !permissionsDiff) {
            logger.info(user.username + ' already have required permissions: ' + user.permissions.join(", "))
        } else {
            //TODO Investigate how it is possible to handle if user already have permissions but it differ from proposed
            logger.warning('=(')
        }
    }
    /**
     * Assign permissions for user
     * @param tenant
     * @param user
     * @param permissions
     */
    void assignUserPermissions(OkapiTenant tenant, OkapiUser user, ArrayList permissions) {
        String uri = '/perms/users/' + user.permissionsId + '/permissions'
        this.headers['X-Okapi-Tenant'] = tenant.getId()
        this.headers['X-Okapi-Token'] = tenant.getAdmin_user().getToken() ? tenant.getAdmin_user().getToken() : ''
        if (permissions) {
            logger.info('Assign permissions to ' + user.username)
            Integer count = 0
            permissions.each {
                if (!user.permissions.contains(it)) {
                    String json = JsonOutput.toJson([
                        permissionName: it
                    ])
                    def res = http.request(method: 'POST', url: okapiUrl, uri: uri, headers: headers, body: json)
                    if (res['status_code'].toInteger() == 200) {
                        logger.info(json)
                        count++
                    } else {
                        logger.error('Can not able to add permission: ' + it + '. Status code: ' + res['status_code'])
                    }
                }
            }
            if (count > 0) {
                logger.info('Wait a minute for permissions cache update')
                sleep(60000)
            }
        } else {
            logger.warning('Permissions list fetched from okapi is empty')
        }
    }
}
