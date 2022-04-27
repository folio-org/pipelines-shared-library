package org.folio.rest

import groovy.json.JsonOutput
import org.folio.http.HttpClient
import org.folio.utilities.Logger
import org.folio.utilities.Tools

class Authorization implements Serializable {
    def steps
    private String okapiUrl = 'localhost:9130'
    private LinkedHashMap headers = ['Content-Type': 'application/json']

    private Users users = new Users(steps, okapiUrl)

    private Tools tools = new Tools()
    private HttpClient http = new HttpClient()
    private Logger logger = new Logger(steps, this.getClass().getCanonicalName())

    Authorization(steps, okapiUrl) {
        this.steps = steps
        this.okapiUrl = okapiUrl
    }

    /**
     * Get information about user credentials
     * @param tenant
     * @param user
     * @return
     */
    def getUserCredentials(OkapiTenant tenant, OkapiUser user) {
        users.validateUser(user)
        if (!user.uuid) {
            throw new Exception(user.username + ' uuid does not specified')
        }
        String uri = '/authn/credentials-existence?userId=' + user.uuid
        this.headers['X-Okapi-Tenant'] = tenant.getId()
        this.headers['X-Okapi-Token'] = tenant.getAdmin_user().getToken() ? tenant.getAdmin_user().getToken() : ''
        def res = http.request(method: 'GET', url: okapiUrl, uri: uri, headers: headers)
        if (res['status_code'].toInteger() == 200) {
            return tools.jsonParse(res['response'])
        } else {
            throw new Exception('Can not get user credentials. Status code:' + res['status_code'])
        }
    }

    /**
     * Create credentials entry for user
     * @param tenant
     * @param user
     */
    void createUserCredentials(OkapiTenant tenant, OkapiUser user) {
        users.validateUser(user)
        String uri = '/authn/credentials'
        this.headers['X-Okapi-Tenant'] = tenant.getId()
        this.headers['X-Okapi-Token'] = tenant.getAdmin_user().getToken() ? tenant.getAdmin_user().getToken() : ''
        if (!getUserCredentials(tenant, user).credentialsExist) {
            logger.info(user.username + ' does not have credentials. Creating...')
            String json = JsonOutput.toJson([userId  : user.uuid,
                                             password: user.password])
            def res = http.request(method: 'POST', url: okapiUrl, uri: uri, headers: headers, body: json)
            if (res['status_code'].toInteger() == 201) {
                logger.info(user.username + ' credentials successfully set')
            } else {
                throw new Exception('Can not set credentials for ' + user.username + '. Status code:' + res['status_code'])
            }
        } else {
            logger.info(user.username + ' credentials already set')
        }
    }

    /**
     * Login via bl-users
     * @param tenant
     * @param user
     * @return
     */
    def login(OkapiTenant tenant, OkapiUser user) {
        def login = [:]
        String uri = '/bl-users/login'
        this.headers['X-Okapi-Tenant'] = tenant.getId()
        logger.info('Login as: ' + user.username)
        String json = JsonOutput.toJson([username: user.username,
                                         password: user.password])
        def res = http.request(method: 'POST', url: okapiUrl, uri: uri, headers: headers, body: json)
        if (res['status_code'].toInteger() == 201) {
            login['token'] = res['headers']['x-okapi-token'][0]
            login['uuid'] = tools.jsonParse(res['response']).user.id
            login['permissions'] = tools.jsonParse(res['response']).permissions.permissions
            login['permissionsId'] = tools.jsonParse(res['response']).permissions.id
            return login
        } else {
            throw new Exception('Unable to login as: ' + user.username + '. Status code: ' + res['status_code'])
        }
    }

    /**
     * Get Okapi token
     * @param tenant
     * @param user
     * @return
     */
    def getOkapiToken(OkapiTenant tenant, OkapiUser user) {
        String uri = '/authn/login'
        this.headers['X-Okapi-Tenant'] = tenant.getId()
        String json = JsonOutput.toJson([username: user.username,
                                         password: user.password])
        def res = http.request(method: 'POST', url: okapiUrl, uri: uri, headers: headers, body: json)
        if (res['status_code'].toInteger() == 201) {
            return tools.jsonParse(res['response']).okapiToken
        } else {
            throw new Exception('Unable to get token for: ' + user.username + '. Status code: ' + res['status_code'])
        }
    }
}
