package org.folio.rest

import groovy.json.JsonOutput
import org.folio.http.HttpClient
import org.folio.rest.model.OkapiTenant
import org.folio.rest.model.OkapiUser
import org.folio.utilities.Logger
import org.folio.utilities.Tools

class Users implements Serializable {
    def steps
    private String okapiUrl = 'localhost:9130'
    private LinkedHashMap headers = ['Content-Type': 'application/json']

    private Tools tools = new Tools()
    private HttpClient http = new HttpClient()
    private Logger logger = new Logger(steps, this.getClass().getCanonicalName())

    Users(steps,okapiUrl) {
        this.steps = steps
        this.okapiUrl = okapiUrl
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
        String uri = '/users?query=username%3d%3d' + user.username
        this.headers['X-Okapi-Tenant'] = tenant.getId()
        this.headers['X-Okapi-Token'] = tenant.getAdmin_user().getToken() ? tenant.getAdmin_user().getToken() : ''
        def res = http.request(method: 'GET', url: okapiUrl, uri: uri, headers: headers)
        if (res['status_code'].toInteger() == 200) {
            return tools.jsonParse(res['response'])
        } else {
            throw new Exception('Can not get user details. Status code:' + res['status_code'])
        }
    }

    /**
     * Create user
     * @param tenant
     * @param user
     * @return
     */
    def createUser(OkapiTenant tenant, OkapiUser user) {
        String uri = '/users'
        this.headers['X-Okapi-Tenant'] = tenant.getId()
        this.headers['X-Okapi-Token'] = tenant.getAdmin_user().getToken() ? tenant.getAdmin_user().getToken() : ''
        def checkUser = getUser(tenant, user)
        if (checkUser.totalRecords.toInteger() == 0) {
            logger.info('User ' + user.username + ' does not exists. Creating...')
            String uuid = UUID.randomUUID().toString()
            String json = JsonOutput.toJson([id      : uuid,
                                             username: user.username,
                                             active  : true,
                                             personal: [lastName : user.lastName,
                                                        firstName: user.firstName,
                                                        email    : user.email]])
            def res = http.request(method: 'POST', url: okapiUrl, uri: uri, headers: headers, body: json)
            if (res['status_code'].toInteger() == 201) {
                logger.info('User ' + user.username + ' successfully created')
                return uuid
            } else {
                throw new Exception('Can not create user ' + user.username + '. Status code:' + +res['status_code'])
            }
        } else if (checkUser.totalRecords.toInteger() > 0) {
            logger.info('User ' + user.username + ' already exists. UUID: ' + checkUser.users[0].id)
            return checkUser.users[0].id
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
        String uri = '/users/' + user.uuid
        this.headers['X-Okapi-Tenant'] = tenant.getId()
        this.headers['X-Okapi-Token'] = tenant.getAdmin_user().getToken() ? tenant.getAdmin_user().getToken() : ''
        String json = JsonOutput.toJson(getUser(tenant, user).users[0] << [patronGroup: patronGroupId])
        def res = http.request(method: 'PUT', url: okapiUrl, uri: uri, headers: headers, body: json)
        if (res['status_code'].toInteger() == 204) {
            logger.info('Patron group ' + user.groupName + ' with id ' + patronGroupId + ' assigned for user ' + user.username)
        } else {
            throw new Exception('Can not assign patron group ' + user.groupName + ' for user ' + user.username + '. Status code:' + +res['status_code'])
        }
    }

    /**
     * Get user patron group Id
     * @param tenant
     * @param user
     */
    def getPatronGroupId(OkapiTenant tenant, OkapiUser user) {
        String uri = '/groups'
        this.headers['X-Okapi-Tenant'] = tenant.getId()
        this.headers['X-Okapi-Token'] = tenant.getAdmin_user().getToken() ? tenant.getAdmin_user().getToken() : ''
        def res = http.request(method: 'GET', url: okapiUrl, uri: uri, headers: headers)
        if (res['status_code'].toInteger() == 200) {
            return tools.jsonParse(res['response']).usergroups.findResult { if (it.group == user.groupName) return it.id }
        } else {
            throw new Exception('Can not get patron groups. Status code:' + res['status_code'])
        }
    }
}
