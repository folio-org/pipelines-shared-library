package org.folio.rest

import groovy.json.JsonOutput
import org.folio.http.HttpClient
import org.folio.utilities.Logger
import org.folio.utilities.Tools

class ServicePoints implements Serializable {
    def steps
    private String okapiUrl = 'localhost:9130'
    private LinkedHashMap headers = ['Content-Type': 'application/json']

    private Users users = new Users(steps, okapiUrl)

    private Tools tools = new Tools()
    private HttpClient http = new HttpClient()
    private Logger logger = new Logger(steps, this.getClass().getCanonicalName())

    ServicePoints(steps, okapiUrl) {
        this.steps = steps
        this.okapiUrl = okapiUrl
    }

    /**
     * Get ids of service points
     * @param tenant
     * @return
     */
    def getServicePointsIds(OkapiTenant tenant) {
        String uri = '/service-points'
        this.headers['X-Okapi-Tenant'] = tenant.getId()
        this.headers['X-Okapi-Token'] = tenant.getAdmin_user().getToken() ? tenant.getAdmin_user().getToken() : ''
        def res = http.request(method: 'GET', url: okapiUrl, uri: uri, headers: headers)
        if (res['status_code'].toInteger() == 200) {
            return tools.jsonParse(res['response']).servicepoints*.id
        } else {
            return []
        }
    }

    /**
     * Get service points users record
     * @param tenant
     * @param user
     * @return
     */
    def getServicePointsUsersRecords(OkapiTenant tenant, OkapiUser user) {
        users.validateUser(user)
        String uri = '/service-points-users?query=userId%3d%3d' + user.uuid
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
     * @param tenant
     * @param user
     * @param servicePointsIds
     */
    void createServicePointsUsersRecord(OkapiTenant tenant, OkapiUser user, ArrayList servicePointsIds) {
        users.validateUser(user)
        String uri = '/service-points-users'
        this.headers['X-Okapi-Tenant'] = tenant.getId()
        this.headers['X-Okapi-Token'] = tenant.getAdmin_user().getToken() ? tenant.getAdmin_user().getToken() : ''
        if (servicePointsIds) {
            logger.info('Assign service points ' + servicePointsIds.join(", ") + ' to user ' + user.username)
            String json = JsonOutput.toJson([userId               : user.uuid,
                                             servicePointsIds     : servicePointsIds,
                                             defaultServicePointId: servicePointsIds.first()])
            def res = http.request(method: 'POST', url: okapiUrl, uri: uri, headers: headers, body: json)
            if (res['status_code'].toInteger() == 201) {
                logger.info('Service points ' + servicePointsIds.join(", ") + ' successfully assigned to user ' + user.username)
            } else if (res['status_code'].toInteger() == 422) {
                logger.warning('Unable to proceed request. Status code:' + res['status_code'])
            } else {
                throw new Exception('Can not set credentials for user ' + user.username + '. Status code:' + res['status_code'])
            }
        } else {
            throw new Exception('Service points ids list is empty')
        }

    }
}
