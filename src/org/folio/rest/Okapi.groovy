package org.folio.rest

import groovy.json.JsonOutput
import hudson.AbortException
import org.folio.rest.model.GeneralParameters
import org.folio.rest.model.OkapiTenant
import org.folio.rest.model.OkapiUser

class Okapi extends GeneralParameters {

  public static final String OKAPI_NAME = "okapi"

  private OkapiUser superuser = new OkapiUser()

  private OkapiTenant supertenant = new OkapiTenant(id: 'supertenant')

  private Users users = new Users(steps, okapi_url)

  private Authorization auth = new Authorization(steps, okapi_url)

  private Permissions permissions = new Permissions(steps, okapi_url)

  Okapi(Object steps, String okapi_url, OkapiUser superuser) {
    super(steps, okapi_url)
    this.superuser = superuser
    this.supertenant.setAdminUser(superuser)
  }

  static String getModuleIdFromInstallJson(List install, String moduleName) {
    return install*.id.find { it ==~ /${moduleName}-.*/ }
  }

  static List buildInstallList(List modulesIds, String action) {
    List modulesList = []
    modulesIds.each {
      modulesList << [
        id    : it,
        action: action
      ]
    }
    return modulesList
  }

  def buildInstallJsonByModuleName(String moduleName, String action = 'enable') {
    String moduleId = getEnabledModules()*.instId.find { it ==~ /${moduleName}-.*/ }
    if (moduleId) {
      return [[id    : moduleId,
               action: action]]
    } else {
      throw new AbortException("Missing required module: ${moduleName}")
    }
  }

  /**
   * Bulk fetch modules descriptors from registry
   * @param registries
   */
  void pull(List registries = OkapiConstants.DESCRIPTORS_REPOSITORIES) {
    auth.getOkapiToken(supertenant, supertenant.getAdminUser())
    String url = okapi_url + "/_/proxy/pull/modules"
    ArrayList headers = [[name: 'Content-type', value: "application/json"],
                         [name: 'X-Okapi-Tenant', value: supertenant.getId()],
                         [name: 'X-Okapi-Token', value: supertenant.getAdminUser().getToken() ? supertenant.getAdminUser().getToken() : '', maskValue: true]]
    logger.info("Pulling modules descriptors from ${registries.join(", ")} to Okapi")
    String body = JsonOutput.toJson([urls: registries])
    def res = http.postRequest(url, body, headers)
    if (res.status == HttpURLConnection.HTTP_OK) {
      logger.info("Modules descriptors successfully pulled from ${registries.join(", ")} to Okapi")
    } else {
      throw new AbortException("Error during modules descriptors pull from ${registries.join(", ")} to Okapi." + http.buildHttpErrorMessage(res))
    }
  }

  void publishModulesDescriptors(List descriptor) {
    auth.getOkapiToken(supertenant, supertenant.getAdminUser())
    logger.info("Publish modules descriptors to okapi.")
    String url = okapi_url + "/_/proxy/import/modules"
    ArrayList headers = [[name: 'Content-type', value: "application/json"],
                         [name: 'X-Okapi-Tenant', value: supertenant.getId()],
                         [name: 'X-Okapi-Token', value: supertenant.getAdminUser().getToken() ? supertenant.getAdminUser().getToken() : '', maskValue: true]]

    def json = JsonOutput.toJson(descriptor)
    def res = http.postRequest(url, json, headers)
    if (res.status < 300) {
      logger.info("Modules descriptors successfully published to Okapi")
    } else {
      throw new AbortException("Error during modules descriptors publishing to Okapi." + http.buildHttpErrorMessage(res))
    }
  }

  /**
   * Fetch modules descriptors for specific modules from registry and push them to okapi
   * @param registries
   */
  List composeModulesDescriptors(List modules, List registries = OkapiConstants.DESCRIPTORS_REPOSITORIES) {
    logger.info("Start module descriptors compose")
    List items = []
    modules.each { module ->
      // skip okapi descriptors
      if (!module.id.startsWith(OKAPI_NAME)) {
        // check whether module descriptor of the module already registered
        if (!checkModuleDescriptor(module)) {
          logger.info("Pull module descriptor for '${module.id}' module from registry")
          // search module descriptor for in repositories
          def descriptor = getModuleDescriptor(registries, module)
          if (descriptor) {
            items.add(descriptor)
          } else {
            throw new AbortException("Module descriptor for '${module.id}' module not found.")
          }
        } else {
          logger.info("Skipping module descriptor '${module.id}' as it already exists in target Okapi")
        }
      }
    }
    return items

    // publish found module descriptors to okapi
//        logger.info("Publish found module descriptors to Okapi.")
//        String url = okapi_url + "/_/proxy/import/modules"
//        ArrayList headers = [[name: 'Content-type', value: "application/json"],
//                             [name: 'X-Okapi-Tenant', value: supertenant.getId()],
//                             [name: 'X-Okapi-Token', value: supertenant.getAdminUser().getToken() ? supertenant.getAdminUser().getToken() : '', maskValue: true]]
//
//        def json = JsonOutput.toJson(items)
//        def res = http.postRequest(url, json, headers)
//        if (res.status < 300) {
//            logger.info("Modules descriptors successfully published to Okapi")
//        } else {
//            throw new AbortException("Error during modules descriptors publishing to Okapi." + http.buildHttpErrorMessage(res))
//        }
  }

/**
 * Search for module descriptor in registry
 * @param registries registries
 * @param module module
 * @return module descriptor
 */
  private Object getModuleDescriptor(List registries, module) {
    for (String registry : registries) {
      def response = http.getRequest("${registry}/_/proxy/modules/${module.id}")
      if (response.status == HttpURLConnection.HTTP_OK) {
        return tools.jsonParse(response.content)
      }
    }
  }

/**
 * Check whether module descriptor is already registered in Okapi
 * @param module module
 * @return true or false
 */
  private Boolean checkModuleDescriptor(module) {
    auth.getOkapiToken(supertenant, supertenant.getAdminUser())
    String url = okapi_url + "/_/proxy/modules?filter=${module.id}"
    ArrayList headers = [[name: 'Content-type', value: "application/json"],
                         [name: 'X-Okapi-Tenant', value: supertenant.getId()],
                         [name: 'X-Okapi-Token', value: supertenant.getAdminUser().getToken() ? supertenant.getAdminUser().getToken() : '', maskValue: true]]

    def response = http.getRequest(url, headers)
    if (response.status == HttpURLConnection.HTTP_OK) {
      def result = tools.jsonParse(response.content)
      return result.size() > 0
    } else {
      throw new AbortException("Error during modules descriptors existance check" + http.buildHttpErrorMessage(response))
    }
  }
/**
 * Check if tenant already exists
 * @param tenantId
 * @return
 */
  Boolean isTenantExists(String tenantId) {
    auth.getOkapiToken(supertenant, supertenant.getAdminUser())
    String url = okapi_url + "/_/proxy/tenants/" + tenantId
    ArrayList headers = [[name: 'X-Okapi-Tenant', value: supertenant.getId()],
                         [name: 'X-Okapi-Token', value: supertenant.getAdminUser().getToken() ? supertenant.getAdminUser().getToken() : '', maskValue: true]]
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
   * reindex Elasticsearch to modules for tenant
   * @param tenant
   * @param okapi_url
   */
  def reindexElasticsearch(tenant, admin_user) {
    auth.getOkapiToken(tenant, admin_user)
    String url = okapi_url + "/search/index/inventory/reindex"
    ArrayList headers = [
      [name: 'Content-type', value: "application/json"],
      [name: 'X-Okapi-Tenant', value: tenant.getId()],
      [name: 'X-Okapi-Token', value: tenant.getAdminUser().getToken() ? tenant.getAdminUser().getToken() : '', maskValue: true]
    ]
    logger.info("Starting Elastic Search reindex with recreate flag = ${tenant.getIndex().recreate}")
    String body = "{\"recreateindex_elasticsearch\": ${tenant.getIndex().recreate} }"
    def res = http.postRequest(url, body, headers)
    if (res.status == HttpURLConnection.HTTP_OK) {
      return tools.jsonParse(res.content).id
    } else {
      throw new AbortException("Error during Elastic Search reindex." + http.buildHttpErrorMessage(res))
    }
  }

  def configureLdpDbSettings(tenant, admin_user, body) {
    auth.getOkapiToken(tenant, admin_user)
    String url = okapi_url + "/ldp/config/dbinfo"
    ArrayList headers = [
      [name: 'Content-type', value: "application/json"],
      [name: 'X-Okapi-Tenant', value: tenant.getId()],
      [name: 'X-Okapi-Token', value: tenant.getAdminUser().getToken() ? tenant.getAdminUser().getToken() : '', maskValue: true]
    ]
    logger.info("Starting Configure LDP DB settings")
    def res = http.putRequest(url, body, headers)
    if (res.status == HttpURLConnection.HTTP_OK) {
      return tools.jsonParse(res.content).id
    } else {
      throw new AbortException("Error during Configure LDP DB settings" + http.buildHttpErrorMessage(res))
    }
  }

  def configureLdpSavedQueryRepo(tenant, admin_user, body) {
    auth.getOkapiToken(tenant, admin_user)
    String url = okapi_url + "/ldp/config/sqconfig"
    ArrayList headers = [
      [name: 'Content-type', value: "application/json"],
      [name: 'X-Okapi-Tenant', value: tenant.getId()],
      [name: 'X-Okapi-Token', value: tenant.getAdminUser().getToken() ? tenant.getAdminUser().getToken() : '', maskValue: true]
    ]
    logger.info("Starting Configure Saved Query repo")
    def res = http.putRequest(url, body, headers)
    if (res.status == HttpURLConnection.HTTP_OK) {
      return tools.jsonParse(res.content).id
    } else {
      throw new AbortException("Error during Configure Saved Query repo" + http.buildHttpErrorMessage(res))
    }
  }

  /**
   * check this status Elasticsearch (records)
   * @param tenant
   * @param admin_user
   * @return
   */
  void checkReindex(tenant, jobid) {
    auth.getOkapiToken(tenant, tenant.getAdminUser())
    String url = okapi_url + "/instance-storage/reindex/${jobid}"
    ArrayList headers = [
      [name: 'Content-type', value: "application/json"],
      [name: 'X-Okapi-Tenant', value: tenant.getId()],
      [name: 'X-Okapi-Token', value: tenant.getAdminUser().getToken() ? tenant.getAdminUser().getToken() : '', maskValue: true]
    ]
    steps.timeout(1440) {
      while (true) {
        def res = http.getRequest(url, headers)
        if (res.status == HttpURLConnection.HTTP_OK) {
          if (tools.jsonParse(res.content).jobStatus == "Ids published") {
            logger.info("reindex records to elastic search successfully completed")
            break
          } else {
            logger.info("Waiting timeout, haven't status: Ids published yet." + http.buildHttpErrorMessage(res))
            steps.sleep(10)

          }
        } else {
          throw new AbortException("not possible check id reindex." + http.buildHttpErrorMessage(res))
        }
      }
    }
  }


  /**
   * Create tenant
   * @param tenant
   */
  void createTenant(OkapiTenant tenant) {
    auth.getOkapiToken(supertenant, supertenant.getAdminUser())
    String url = okapi_url + "/_/proxy/tenants"
    ArrayList headers = [[name: 'Content-type', value: "application/json"],
                         [name: 'X-Okapi-Tenant', value: supertenant.getId()],
                         [name: 'X-Okapi-Token', value: supertenant.getAdminUser().getToken() ? supertenant.getAdminUser().getToken() : '', maskValue: true]]
    if (!isTenantExists(tenant.id)) {
      logger.info("Tenant ${tenant.id} does not exists. Creating...")
      String body = JsonOutput.toJson([id         : tenant.id,
                                       name       : tenant.name,
                                       description: tenant.description])
      def res = http.postRequest(url, body, headers)
      if (res.status == HttpURLConnection.HTTP_CREATED) {
        logger.info("Tenant ${tenant.id} successfully created")
      } else {
        throw new AbortException("Tenant ${tenant.id} does not created." + http.buildHttpErrorMessage(res))
      }
    } else {
      logger.info("Tenant ${tenant.id} already exists")
    }
  }

  /**
   * Delete tenant
   * @param tenant
   */
  void deleteTenant(OkapiTenant tenant) {
    auth.getOkapiToken(supertenant, supertenant.getAdminUser())
    String url = okapi_url + "/_/proxy/tenants/" + tenant.id
    ArrayList headers = [[name: 'Content-type', value: "application/json"],
                         [name: 'X-Okapi-Tenant', value: supertenant.getId()],
                         [name: 'X-Okapi-Token', value: supertenant.getAdminUser().getToken() ? supertenant.getAdminUser().getToken() : '', maskValue: true]]
    if (isTenantExists(tenant.id)) {
      logger.info("Tenant ${tenant.id} exists. Deleting...")
      String body = JsonOutput.toJson([id: tenant.id])

      def res = http.deleteRequest(url, body, headers)
      logger.info("DEBUG ${okapi_url} okapi URL")
      if (res.status == HttpURLConnection.HTTP_NO_CONTENT) {
        logger.info("Tenant ${tenant.id} successfully deleted")
      } else {
        throw new AbortException("Tenant ${tenant.id} does not deleted." + http.buildHttpErrorMessage(res))
      }
    } else {
      logger.info("Tenant ${tenant.id} does not exist")
    }
  }

/**
 * Build query based on tenant parameters
 * @param parameters
 * @return
 */
  static def buildTenantQueryParameters(OkapiTenant tenant) {
    String tenantParameters = 'tenantParameters='
    String queryParameters = ''
    String parameters = ''
    if (tenant.getTenantParameters()) {
      tenant.getTenantParameters().eachWithIndex { it, i ->
        tenantParameters += it.key + '%3D' + it.value
        if (i != tenant.getTenantParameters().size() - 1) {
          tenantParameters += '%2C'
        }
      }
      parameters = '?' + tenantParameters
    }
    if (tenant.getQueryParameters()) {
      tenant.getQueryParameters().eachWithIndex { it, i ->
        queryParameters += it.key + '=' + it.value
        if (i != tenant.getQueryParameters().size() - 1) {
          queryParameters += '&'
        }
      }
      if (parameters?.trim()) {
        parameters += '&' + queryParameters
      } else {
        parameters = '?' + queryParameters
      }
    }
    return parameters
  }

/**
 * Bulk Enable/Disable/Upgrade modules for tenant
 * @param tenant
 * @param list
 * @param timeout
 * @return
 */
  def enableDisableUpgradeModulesForTenant(OkapiTenant tenant, ArrayList modulesList, Integer timeout = 0) {
    auth.getOkapiToken(supertenant, supertenant.getAdminUser())
    String tenantParameters = buildTenantQueryParameters(tenant)
    //TODO move reinstall to options
    String url = okapi_url + "/_/proxy/tenants/" + tenant.id + "/install" + tenantParameters
//        String url = okapi_url + "/_/proxy/tenants/" + tenant.id + "/install" + tenantParameters + '&reinstall=true'
    ArrayList headers = [[name: 'Content-type', value: "application/json"],
                         [name: 'X-Okapi-Tenant', value: supertenant.getId()],
                         [name: 'X-Okapi-Token', value: supertenant.getAdminUser().getToken() ? supertenant.getAdminUser().getToken() : '', maskValue: true]]
    String body = JsonOutput.toJson(modulesList)
    logger.info("Install operation for tenant ${tenant.id} started")
    def res = http.postRequest(url, body, headers, true, timeout)

    if (res.status == HttpURLConnection.HTTP_OK) {
      logger.info("Install operation for tenant ${tenant.id} finished successfully\n${JsonOutput.prettyPrint(res.content)}")
      return tools.jsonParse(res.content)
    } else {
      throw new AbortException("Install operation failed. ${url}" + http.buildHttpErrorMessage(res))
    }
  }

  /**
   * Check if module service and instance Ids already registered
   * @param serviceId
   * @return
   */
  Boolean isServiceExists(Map service) {
    auth.getOkapiToken(supertenant, supertenant.getAdminUser())
    String url = okapi_url + "/_/discovery/modules/" + service['srvcId']
    ArrayList headers = [[name: 'X-Okapi-Tenant', value: supertenant.getId()],
                         [name: 'X-Okapi-Token', value: supertenant.getAdminUser().getToken() ? supertenant.getAdminUser().getToken() : '', maskValue: true]]
    def res = http.getRequest(url, headers, true)
    if (res.status == HttpURLConnection.HTTP_OK) {
      if (tools.jsonParse(res.content)[0].url == service['url']) {
        return true
      } else {
        throw new AbortException("Registered module has incorrect url." + http.buildHttpErrorMessage(res))
      }
    } else if (res.status == HttpURLConnection.HTTP_NOT_FOUND) {
      return false
    } else {
      throw new AbortException("Can not able to check service ${service['srvcId']} existence." + http.buildHttpErrorMessage(res))
    }
  }

/**
 * Register modules descriptors in Okapi if not registered
 * @param discoveryList
 */
  void registerServices(ArrayList discoveryList) {
    auth.getOkapiToken(supertenant, supertenant.getAdminUser())
    String url = okapi_url + "/_/discovery/modules"
    ArrayList headers = [[name: 'Content-type', value: "application/json"],
                         [name: 'X-Okapi-Tenant', value: supertenant.getId()],
                         [name: 'X-Okapi-Token', value: supertenant.getAdminUser().getToken() ? supertenant.getAdminUser().getToken() : '', maskValue: true]]
    logger.info("Modules registration in Okapi. Starting...")
    discoveryList.each {
      if (it['url'] && it['srvcId'] && it['instId']) {
        if (!isServiceExists(it)) {
          logger.info("${it['srvcId']} not registered. Registering...")
          String body = JsonOutput.toJson(it)
          steps.timeout(15) {
            while (true) {
              def res = http.postRequest(url, body, headers, true)
              if (res.status == HttpURLConnection.HTTP_CREATED) {
                logger.info("${it['srvcId']} registered successfully")
                break
              } else if (res.status == HttpURLConnection.HTTP_NOT_FOUND) {
                logger.info("${it['srvcId']} is not registered." + http.buildHttpErrorMessage(res))
                logger.info("Repeat ${it['srvcId']} registration in 3 seconds.")
                steps.sleep(3)
              } else {
                throw new AbortException("${it['srvcId']} is not registered." + http.buildHttpErrorMessage(res))
              }
            }
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
    auth.getOkapiToken(supertenant, supertenant.getAdminUser())
    String url = okapi_url + "/_/discovery/modules"
    ArrayList headers = [[name: 'X-Okapi-Tenant', value: supertenant.getId()],
                         [name: 'X-Okapi-Token', value: supertenant.getAdminUser().getToken() ? supertenant.getAdminUser().getToken() : '', maskValue: true]]
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
    auth.getOkapiToken(supertenant, supertenant.getAdminUser())
    String url = okapi_url + "/_/proxy/tenants/" + tenant.id + "/interfaces/" + moduleName
    ArrayList headers = [[name: 'X-Okapi-Tenant', value: supertenant.getId()],
                         [name: 'X-Okapi-Token', value: supertenant.getAdminUser().getToken() ? supertenant.getAdminUser().getToken() : '', maskValue: true]]
    def res = http.getRequest(url, headers)
    if (res.status == HttpURLConnection.HTTP_OK) {
      return tools.jsonParse(res.content).id[0]
    } else {
      return ''
    }
  }

  void secure(OkapiUser user) {
    auth.getOkapiToken(supertenant, supertenant.getAdminUser())
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

  void unsecure() {
    auth.getOkapiToken(supertenant, supertenant.getAdminUser())
    //TODO temporary solution (required when supertenant on env have not actual versions)
//        def updateModules = ['mod-users'      : buildInstallJsonByModuleName('mod-users', 'enable'),
//                               'mod-permissions': buildInstallJsonByModuleName('mod-permissions', 'enable'),
//                               'mod-login'      : buildInstallJsonByModuleName('mod-login', 'enable'),
//                               'mod-authtoken'  : buildInstallJsonByModuleName('mod-authtoken', 'enable')]
//        enableDisableUpgradeModulesForTenant(supertenant, updateModules['mod-authtoken'])
//        enableDisableUpgradeModulesForTenant(supertenant, updateModules['mod-login'])
//        enableDisableUpgradeModulesForTenant(supertenant, updateModules['mod-permissions'])
//        enableDisableUpgradeModulesForTenant(supertenant, updateModules['mod-users'])
    def requiredModules = ['mod-users'      : buildInstallJsonByModuleName('mod-users', 'disable'),
                           'mod-permissions': buildInstallJsonByModuleName('mod-permissions', 'disable'),
                           'mod-login'      : buildInstallJsonByModuleName('mod-login', 'disable'),
                           'mod-authtoken'  : buildInstallJsonByModuleName('mod-authtoken', 'disable')]
    enableDisableUpgradeModulesForTenant(supertenant, requiredModules['mod-authtoken'])
    enableDisableUpgradeModulesForTenant(supertenant, requiredModules['mod-login'])
    enableDisableUpgradeModulesForTenant(supertenant, requiredModules['mod-permissions'])
    enableDisableUpgradeModulesForTenant(supertenant, requiredModules['mod-users'])
  }

  void cleanupServicesRegistration() {
    auth.getOkapiToken(supertenant, supertenant.getAdminUser())
    String url = okapi_url + "/_/discovery/modules"
    ArrayList headers = [[name: 'Content-type', value: "application/json"],
                         [name: 'X-Okapi-Tenant', value: supertenant.getId()],
                         [name: 'X-Okapi-Token', value: supertenant.getAdminUser().getToken() ? supertenant.getAdminUser().getToken() : '', maskValue: true]]
    logger.info("Okapi discovery table cleanup. Starting...")
    String body = JsonOutput.toJson("")
    def res = http.deleteRequest(url, body, headers)
    if (res.status == HttpURLConnection.HTTP_NO_CONTENT) {
      logger.info("Okapi discovery table cleanup finished successfully")
    } else {
      throw new AbortException("Error during okapi discovery table cleanup: " + http.buildHttpErrorMessage(res))
    }
  }

  List getInstalledModules(String tenant_id) {
    auth.getOkapiToken(supertenant, supertenant.getAdminUser())
    String url = okapi_url + "/_/proxy/tenants/${tenant_id}/modules"
    ArrayList headers = [[name: 'Content-type', value: "application/json"],
                         [name: 'X-Okapi-Tenant', value: supertenant.getId()],
                         [name: 'X-Okapi-Token', value: supertenant.getAdminUser().getToken() ? supertenant.getAdminUser().getToken() : '', maskValue: true]]
    def res = http.getRequest(url, headers)
    if (res.status == HttpURLConnection.HTTP_OK) {
      def test = tools.jsonParse(res.content)
      println(test)
      return tools.jsonParse(res.content)
    } else {
      throw new AbortException("Unable to retrieve installed modules list." + http.buildHttpErrorMessage(res))
    }
  }

  def getTenantList() {
    auth.getOkapiToken(supertenant, supertenant.getAdminUser())
    String url = okapi_url + "/_/proxy/tenants"
    ArrayList headers = [[name: 'Content-type', value: "application/json"],
                         [name: 'X-Okapi-Tenant', value: supertenant.getId()],
                         [name: 'X-Okapi-Token', value: supertenant.getAdminUser().getToken() ? supertenant.getAdminUser().getToken() : '', maskValue: true]]
    def res = http.getRequest(url, headers)
    if (res.status == HttpURLConnection.HTTP_OK) {
      def test = tools.jsonParse(res.content)
      return test.id
    } else {
      throw new AbortException("Unable to retrieve installed modules list." + http.buildHttpErrorMessage(res))
    }
  }

  List checkInstalledModules(String tenantId, int timeout) {
    long endTime = System.currentTimeMillis() + timeout * 60 * 60 * 1000
    auth.getOkapiToken(supertenant, supertenant.getAdminUser())
    String url = okapi_url + "/_/proxy/tenants/${tenantId}/install"
    ArrayList headers = [
      [name: 'Content-type', value: 'application/json'],
      [name: 'X-Okapi-Tenant', value: supertenant.getId()],
      [name: 'X-Okapi-Token', value: supertenant.getAdminUser().getToken() ?: '', maskValue: true]
    ]

    while (System.currentTimeMillis() < endTime) {
      def response = http.getRequest(url, headers)

      if (response.status == HttpURLConnection.HTTP_OK) {
        def installedModules = tools.jsonParse(response.content)
        def inProgressCount = 0
        installedModules.each { moduleGroup ->
          moduleGroup.modules.each { module ->
            if (module.containsKey('message')) {
              throw new AbortException("Module '${module.id}' failed. Error message: ${module.message}")
            } else if (module.stage == 'invoke' || module.stage == 'pending') {
              inProgressCount++
              logger.info("${module.id} in ${module.stage} status")
            }
          }
        }

        if (inProgressCount == 0) {
          logger.info("All modules are enabled")
          break
        } else {
          logger.info("${inProgressCount} modules still are not enabled. Sleep for 15 minutes ..")
          sleep(900000)
        }
      } else {
        throw new AbortException("Unable to retrieve installed modules list. ${http.buildHttpErrorMessage(response)}")
      }
    }
    // Abort job if current time more
    if (System.currentTimeMillis() > endTime) {
      throw new AbortException("Timeout: Unable to complete module actions within the specified time.")
    }
  }
}
