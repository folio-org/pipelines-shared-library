package org.folio.rest

import groovy.json.JsonOutput
import org.folio.http.HttpClient
import org.folio.utilities.Logger
import org.folio.utilities.Tools

class Okapi implements Serializable {
    def steps
    OkapiUser superuser = new OkapiUser()
    OkapiTenant supertenant = new OkapiTenant(id: 'supertenant')
    private LinkedHashMap headers = ['Content-Type': 'application/json']

    private String okapiUrl = 'localhost:9130'
    private Users users = new Users(steps, okapiUrl)
    private Permissions permissions = new Permissions(steps, okapiUrl)
    private Authorization auth = new Authorization(steps, okapiUrl)

    private Tools tools = new Tools()
    private HttpClient http = new HttpClient()
    private Logger logger = new Logger(steps, this.getClass().getCanonicalName())

    Okapi(steps, okapiUrl) {
        this.steps = steps
        this.okapiUrl = okapiUrl
    }

    void superuserLogin() {
        if (superuser.username && superuser.password) {
            superuser.setToken(auth.getOkapiToken(supertenant, superuser))
        }
    }

    void setSuperuser(superuser) {
        this.superuser = superuser
        superuserLogin()
        supertenant.setAdmin_user(superuser)
    }

    /**
     * Bulk fetch modules descriptors from registry
     * @param registries
     */
    void pull(List registries = ['http://folio-registry.aws.indexdata.com']) {
        String uri = '/_/proxy/pull/modules'
        this.headers['X-Okapi-Tenant'] = supertenant.getId()
        this.headers['X-Okapi-Token'] = superuser.getToken() ? superuser.getToken() : ''
        logger.info('Pulling modules descriptors from ' + registries.join(", ") + ' to Okapi')
        String json = JsonOutput.toJson([urls: registries])
        def res = http.request(method: 'POST', url: okapiUrl, uri: uri, headers: headers, body: json)
        if (res['status_code'].toInteger() == 200) {
            logger.info('Modules descriptors pulled from ' + registries.join(", ") + ' to Okapi successfully')
        } else {
            throw new Exception('Error during modules descriptors pull from ' + registries.join(", ") + ' to Okapi. Status code: ' + res['status_code'])
        }
    }

    /**
     * Check if tenant already exists
     * @param tenantId
     * @return
     */
    Boolean isTenantExists(String tenantId) {
        String uri = '/_/proxy/tenants/' + tenantId
        this.headers['X-Okapi-Tenant'] = supertenant.getId()
        this.headers['X-Okapi-Token'] = superuser.getToken() ? superuser.getToken() : ''
        def res = http.request(method: 'GET', url: okapiUrl, uri: uri, headers: headers)
        if (res['status_code'].toInteger() == 200) {
            return true
        } else if (res['status_code'].toInteger() == 404) {
            return false
        } else {
            throw new Exception('Can not able to check tenant ' + tenantId + ' existence. Status code: ' + res['status_code'])
        }
    }

    /**
     * Create tenant
     * @param tenant
     */
    void createTenant(OkapiTenant tenant) {
        String uri = '/_/proxy/tenants'
        this.headers['X-Okapi-Tenant'] = supertenant.getId()
        this.headers['X-Okapi-Token'] = superuser.getToken() ? superuser.getToken() : ''
        if (!isTenantExists(tenant.id)) {
            logger.info('Tenant ' + tenant.id + ' does not exists. Creating...')
            String json = JsonOutput.toJson([id         : tenant.id,
                                             name       : tenant.name,
                                             description: tenant.description])
            def res = http.request(method: 'POST', url: okapiUrl, uri: uri, headers: headers, body: json)
            if (res['status_code'].toInteger() == 201) {
                logger.info('Tenant ' + tenant.id + ' successfully created')
                enableModuleForTenant(tenant, 'okapi')
            } else {
                throw new Exception('Tenant ' + tenant.id + ' does not created. Status code: ' + res['status_code'])
            }
        } else {
            logger.info('Tenant ' + tenant.id + ' already exists')
        }
    }

    /**
     * Enable one module for tenant
     * @param tenant
     * @param moduleName
     */
    void enableModuleForTenant(OkapiTenant tenant, String moduleName) {
        String uri = '/_/proxy/tenants/' + tenant.id + '/modules'
        this.headers['X-Okapi-Tenant'] = supertenant.getId()
        this.headers['X-Okapi-Token'] = superuser.getToken() ? superuser.getToken() : ''
        logger.info('Enabling module: ' + moduleName + ' for tenant: ' + tenant.id)
        String json = JsonOutput.toJson([id: moduleName])
        def res = http.request(method: 'POST', url: okapiUrl, uri: uri, headers: headers, body: json)
        if (res['status_code'].toInteger() == 201) {
            logger.info('Module: ' + moduleName + ' successfully enabled for tenant: ' + tenant.id)
        } else if (res['status_code'].toInteger() == 400) {
            logger.info('Module: ' + moduleName + ' already enabled for tenant: ' + tenant.id)
        } else {
            throw new Exception('Unable to enable module ' + moduleName + ' for tenant ' + tenant.id + '. Status code: ' + res['status_code'])
        }
    }

    /**
     * Build query based on tenant parameters
     * @param parameters
     * @return
     */
    static def buildTenantQueryParameters(LinkedHashMap parameters) {
        String tenantParameters = ''
        if (parameters) {
            tenantParameters = '?tenantParameters='
            parameters.eachWithIndex { it, i ->
                tenantParameters += it.key + '%3D' + it.value
                if (i != parameters.size() - 1) {
                    tenantParameters += '%2C'
                }
            }
        }
        return tenantParameters
    }

    /**
     * Bulk Enable/Disable/Upgrade modules for tenant
     * @param tenant
     * @param list
     * @param timeout
     * @return
     */
    def enableDisableUpgradeModulesForTenant(OkapiTenant tenant, ArrayList modulesList, Integer timeout = 0) {
        String queryParameters = buildTenantQueryParameters(tenant.parameters)
        String uri = '/_/proxy/tenants/' + tenant.id + '/install' + queryParameters
        this.headers['X-Okapi-Tenant'] = supertenant.getId()
        this.headers['X-Okapi-Token'] = superuser.getToken() ? superuser.getToken() : ''
        String json = JsonOutput.toJson(modulesList)
        logger.info('Install operation for tenant ' + tenant.id + ' started')
        def res = http.request(method: 'POST', url: okapiUrl, uri: uri, headers: headers, body: json, timeout: timeout)
        if (res['status_code'].toInteger() == 200) {
            logger.info('Install operation for tenant ' + tenant.id + ' finished successfully\n' + JsonOutput.prettyPrint(res['response']))
            return tools.jsonParse(res['response'])
        } else {
            throw new Exception('Install operation failed. Status code:' + res['status_code'])
        }
    }

    /**
     * Check if module service and instance Ids already registered
     * @param serviceId
     * @return
     */
    Boolean isServiceExists(String serviceId) {
        String uri = '/_/discovery/modules/' + serviceId
        this.headers['X-Okapi-Tenant'] = supertenant.getId()
        this.headers['X-Okapi-Token'] = superuser.getToken() ? superuser.getToken() : ''
        def res = http.request(method: 'GET', url: okapiUrl, uri: uri, headers: headers)
        if (res['status_code'].toInteger() == 200) {
            return true
        } else if (res['status_code'].toInteger() == 404) {
            return false
        } else {
            throw new Exception('Can not able to check service ' + serviceId + ' existence. Status code: ' + res['status_code'])
        }
    }

    /**
     * Register modules descriptors in Okapi if not registered
     * @param discoveryList
     */
    void registerServices(ArrayList discoveryList) {
        String uri = '/_/discovery/modules'
        this.headers['X-Okapi-Tenant'] = supertenant.getId()
        this.headers['X-Okapi-Token'] = superuser.getToken() ? superuser.getToken() : ''
        logger.info('Modules registration in Okapi. Starting...')
        discoveryList.each {
            if (it['url'] && it['srvcId'] && it['instId']) {
                if (!isServiceExists(it['srvcId'])) {
                    logger.info(it['srvcId'] + ' not registered. Registering...')
                    String json = JsonOutput.toJson(it)
                    def res = http.request(method: 'POST', url: okapiUrl, uri: uri, headers: headers, body: json)
                    if (res['status_code'].toInteger() == 201) {
                        logger.info(it['srvcId'] + ' registered successfully')
                    } else {
                        throw new Exception(it['srvcId'] + ' does not registered. Status code: ' + res['status_code'])
                    }
                } else {
                    logger.info(it['srvcId'] + ' already exists')
                }
            } else {
                throw new Exception(it + ': One of required field (srvcId, instId or url) are missing')
            }
        }
        logger.info('Modules registration in Okapi finished successfully')
    }

    /**
     * Get all enabled services
     * @return
     */
    def getEnabledSerivces() {
        String uri = '/_/discovery/modules'
        this.headers['X-Okapi-Tenant'] = supertenant.getId()
        this.headers['X-Okapi-Token'] = superuser.getToken() ? superuser.getToken() : ''
        def res = http.request(method: 'GET', url: okapiUrl, uri: uri, headers: headers)
        if (res['status_code'].toInteger() == 200) {
            return tools.jsonParse(res['response'])
        } else {
            throw new Exception('Unable to retrieve enabled services list. Status code:' + res['status_code'])
        }
    }

    /**
     * Get particular module id by name
     * @param tenant
     * @param moduleName
     * @return
     */
    def getModuleId(OkapiTenant tenant, String moduleName) {
        String uri = '/_/proxy/tenants/' + tenant.id + '/interfaces/' + moduleName
        this.headers['X-Okapi-Tenant'] = supertenant.getId()
        this.headers['X-Okapi-Token'] = superuser.getToken() ? superuser.getToken() : ''
        def res = http.request(method: 'GET', url: okapiUrl, uri: uri, headers: headers)
        if (res['status_code'].toInteger() == 200) {
            return tools.jsonParse(res['response']).id[0]
        } else {
            return ''
        }
    }

    void secure(OkapiUser user) {
        def requiredModules = [
            'mod-users',
            'mod-permissions',
            'mod-login',
            'mod-authtoken'
        ]
        requiredModules.each {
            if (!getEnabledSerivces()*.instId.any { module -> module ==~ /${it}-.*/ }) {
                throw new Exception('Missing required module: ' + it)
            }
        }
        enableModuleForTenant(supertenant, 'mod-users')
        enableModuleForTenant(supertenant, 'mod-permissions')
        user.setUuid(users.createUser(supertenant, user))
        enableModuleForTenant(supertenant, 'mod-login')
        user.setPermissions(["perms.users.assign.immutable", "perms.users.assign.mutable", "perms.users.assign.okapi", "perms.all", "okapi.all", "okapi.proxy.pull.modules.post", "login.all", "users.all"])
        permissions.createUserPermissions(supertenant, user)
        auth.createUserCredentials(supertenant, user)
        enableModuleForTenant(supertenant, 'mod-authtoken')
    }
}
