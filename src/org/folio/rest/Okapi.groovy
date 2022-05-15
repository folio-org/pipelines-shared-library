package org.folio.rest


import hudson.AbortException
import groovy.json.JsonOutput
import org.folio.rest.model.GeneralParameters
import org.folio.rest.model.OkapiTenant
import org.folio.rest.model.OkapiUser

class Okapi extends GeneralParameters {

    private OkapiUser superuser = new OkapiUser()

    private OkapiTenant supertenant = new OkapiTenant(id: 'supertenant')

    private Users users = new Users(steps, okapiUrl)

    private Authorization auth = new Authorization(steps, okapiUrl)

    private Permissions permissions = new Permissions(steps, okapiUrl)

    Okapi(Object steps, String okapiUrl, OkapiUser superuser) {
        super(steps, okapiUrl)
        this.superuser = superuser
        this.supertenant.setAdmin_user(superuser)
    }

    /**
     * Bulk fetch modules descriptors from registry
     * @param registries
     */
    void pull(List registries = OkapiConstants.DESCRIPTORS_REPOSITORIES) {
        auth.getOkapiToken(supertenant, superuser)
        String url = okapiUrl + "/_/proxy/pull/modules"
        ArrayList headers = [[name: 'Content-type', value: "application/json"],
                             [name: 'X-Okapi-Tenant', value: supertenant.getId()],
                             [name: 'X-Okapi-Token', value: supertenant.getAdmin_user().getToken() ? supertenant.getAdmin_user().getToken() : '', maskValue: true]]
        logger.info("Pulling modules descriptors from ${registries.join(", ")} to Okapi")
        String body = JsonOutput.toJson([urls: registries])
        def res = http.postRequest(url, body, headers)
        if (res.status == HttpURLConnection.HTTP_OK) {
            logger.info("Modules descriptors successfully pulled from ${registries.join(", ")} to Okapi")
        } else {
            throw new AbortException("Error during modules descriptors pull from ${registries.join(", ")} to Okapi." + http.buildHttpErrorMessage(res))
        }
    }

    /**
     * Check if tenant already exists
     * @param tenantId
     * @return
     */
    Boolean isTenantExists(String tenantId) {
        auth.getOkapiToken(supertenant, superuser)
        String url = okapiUrl + "/_/proxy/tenants/" + tenantId
        ArrayList headers = [[name: 'X-Okapi-Tenant', value: supertenant.getId()],
                             [name: 'X-Okapi-Token', value: supertenant.getAdmin_user().getToken() ? supertenant.getAdmin_user().getToken() : '', maskValue: true]]
        def res = http.getRequest(url, headers)
        if (res.status == HttpURLConnection.HTTP_OK) {
            return true
        } else if (res.status == HttpURLConnection.HTTP_NOT_FOUND) {
            return false
        } else {
            throw new AbortException("Can not able to check tenant ${tenantId} existence." + http.buildHttpErrorMessage(res))
        }
    }

    /**
     * Create tenant
     * @param tenant
     */
    void createTenant(OkapiTenant tenant) {
        auth.getOkapiToken(supertenant, superuser)
        String url = okapiUrl + "/_/proxy/tenants"
        ArrayList headers = [[name: 'Content-type', value: "application/json"],
                             [name: 'X-Okapi-Tenant', value: supertenant.getId()],
                             [name: 'X-Okapi-Token', value: supertenant.getAdmin_user().getToken() ? supertenant.getAdmin_user().getToken() : '', maskValue: true]]
        if (!isTenantExists(tenant.id)) {
            logger.info("Tenant ${tenant.id} does not exists. Creating...")
            String body = JsonOutput.toJson([id         : tenant.id,
                                             name       : tenant.name,
                                             description: tenant.description])
            def res = http.postRequest(url, body, headers)
            if (res.status == HttpURLConnection.HTTP_CREATED) {
                logger.info("Tenant ${tenant.id} successfully created")
//                enableDisableUpgradeModulesForTenant(supertenant, buildInstallJsonByModuleName('okapi'))
            } else {
                throw new AbortException("Tenant ${tenant.id} does not created." + http.buildHttpErrorMessage(res))
            }
        } else {
            logger.info("Tenant ${tenant.id} already exists")
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
        auth.getOkapiToken(supertenant, superuser)
        String queryParameters = buildTenantQueryParameters(tenant.parameters)
        String url = okapiUrl + "/_/proxy/tenants/" + tenant.id + "/install" + queryParameters
        ArrayList headers = [[name: 'Content-type', value: "application/json"],
                             [name: 'X-Okapi-Tenant', value: supertenant.getId()],
                             [name: 'X-Okapi-Token', value: supertenant.getAdmin_user().getToken() ? supertenant.getAdmin_user().getToken() : '', maskValue: true]]
        String body = JsonOutput.toJson(modulesList)
        logger.info("Install operation for tenant ${tenant.id} started")
        def res = http.postRequest(url, body, headers, true, timeout)
        if (res.status == HttpURLConnection.HTTP_OK) {
            logger.info("Install operation for tenant ${tenant.id} finished successfully\n${JsonOutput.prettyPrint(res.content)}")
            return tools.jsonParse(res.content)
        } else {
            throw new AbortException("Install operation failed." + http.buildHttpErrorMessage(res))
        }
    }

    /**
     * Check if module service and instance Ids already registered
     * @param serviceId
     * @return
     */
    Boolean isServiceExists(String serviceId) {
        auth.getOkapiToken(supertenant, superuser)
        String url = okapiUrl + "/_/discovery/modules/" + serviceId
        ArrayList headers = [[name: 'X-Okapi-Tenant', value: supertenant.getId()],
                             [name: 'X-Okapi-Token', value: supertenant.getAdmin_user().getToken() ? supertenant.getAdmin_user().getToken() : '', maskValue: true]]
        def res = http.getRequest(url, headers, true)
        if (res.status == HttpURLConnection.HTTP_OK) {
            return true
        } else if (res.status == HttpURLConnection.HTTP_NOT_FOUND) {
            return false
        } else {
            throw new AbortException("Can not able to check service ${serviceId} existence." + http.buildHttpErrorMessage(res))
        }
    }

    /**
     * Register modules descriptors in Okapi if not registered
     * @param discoveryList
     */
    void registerServices(ArrayList discoveryList) {
        auth.getOkapiToken(supertenant, superuser)
        String url = okapiUrl + "/_/discovery/modules"
        ArrayList headers = [[name: 'Content-type', value: "application/json"],
                             [name: 'X-Okapi-Tenant', value: supertenant.getId()],
                             [name: 'X-Okapi-Token', value: supertenant.getAdmin_user().getToken() ? supertenant.getAdmin_user().getToken() : '', maskValue: true]]
        logger.info("Modules registration in Okapi. Starting...")
        discoveryList.each {
            if (it['url'] && it['srvcId'] && it['instId']) {
                if (!isServiceExists(it['srvcId'])) {
                    logger.info("${it['srvcId']} not registered. Registering...")
                    String body = JsonOutput.toJson(it)
                    def res = http.postRequest(url, body, headers, true)
                    if (res.status == HttpURLConnection.HTTP_CREATED) {
                        logger.info("${it['srvcId']} registered successfully")
                    } else {
                        throw new AbortException("${it['srvcId']} does not registered." + http.buildHttpErrorMessage(res))
                    }
                } else {
                    logger.info("${it['srvcId']} already exists")
                }
            } else {
                throw new AbortException("${it}: One of required field (srvcId, instId or url) are missing")
            }
        }
        logger.info("Modules registration in Okapi finished successfully")
    }

    /**
     * Get all enabled services
     * @return
     */
    def getEnabledModules() {
        auth.getOkapiToken(supertenant, superuser)
        String url = okapiUrl + "/_/discovery/modules"
        ArrayList headers = [[name: 'X-Okapi-Tenant', value: supertenant.getId()],
                             [name: 'X-Okapi-Token', value: supertenant.getAdmin_user().getToken() ? supertenant.getAdmin_user().getToken() : '', maskValue: true]]
        def res = http.getRequest(url, headers)
        if (res.status == HttpURLConnection.HTTP_OK) {
            return tools.jsonParse(res.content)
        } else {
            throw new AbortException("Unable to retrieve enabled services list." + http.buildHttpErrorMessage(res))
        }
    }

    /**
     * Get particular module id by name
     * @param tenant
     * @param moduleName
     * @return
     */
    def getModuleId(OkapiTenant tenant, String moduleName) {
        auth.getOkapiToken(supertenant, superuser)
        String url = okapiUrl + "/_/proxy/tenants/" + tenant.id + "/interfaces/" + moduleName
        ArrayList headers = [[name: 'X-Okapi-Tenant', value: supertenant.getId()],
                             [name: 'X-Okapi-Token', value: supertenant.getAdmin_user().getToken() ? supertenant.getAdmin_user().getToken() : '', maskValue: true]]
        def res = http.getRequest(url, headers)
        if (res.status == HttpURLConnection.HTTP_OK) {
            return tools.jsonParse(res.content).id[0]
        } else {
            return ''
        }
    }

    def buildInstallJsonByModuleName(String moduleName) {
        String moduleId = getEnabledModules()*.instId.find { it ==~ /${moduleName}-.*/ }
        if (moduleId) {
            return [[id    : moduleId,
                     action: "enable"]]
        } else {
            throw new AbortException("Missing required module: ${moduleName}")
        }
    }

    void secure(OkapiUser user) {
        auth.getOkapiToken(supertenant, superuser)
        def requiredModules = ['mod-users'      : buildInstallJsonByModuleName('mod-users'),
                               'mod-permissions': buildInstallJsonByModuleName('mod-permissions'),
                               'mod-login'      : buildInstallJsonByModuleName('mod-login'),
                               'mod-authtoken'  : buildInstallJsonByModuleName('mod-authtoken')]
        enableDisableUpgradeModulesForTenant(supertenant, requiredModules['mod-users'])
        enableDisableUpgradeModulesForTenant(supertenant, requiredModules['mod-permissions'])
        users.createUser(supertenant, user)
        enableDisableUpgradeModulesForTenant(supertenant, requiredModules['mod-login'])
        user.setPermissions(OkapiConstants.OKAPI_SUPER_USER_PERMISSIONS)
        permissions.createUserPermissions(supertenant, user)
        auth.createUserCredentials(supertenant, user)
        enableDisableUpgradeModulesForTenant(supertenant, requiredModules['mod-authtoken'])
    }
}
