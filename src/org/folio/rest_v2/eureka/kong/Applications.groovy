package org.folio.rest_v2.eureka.kong

import com.cloudbees.groovy.cps.NonCPS
import org.folio.models.EurekaTenant
import org.folio.models.module.FolioModule
import org.folio.rest_v2.eureka.Keycloak
import org.folio.rest_v2.eureka.Kong
import org.folio.utilities.RequestException

class Applications extends Kong{

  Applications(def context, String kongUrl, Keycloak keycloak, boolean debug = false){
    super(context, kongUrl, keycloak, debug)
  }

  Applications(def context, String kongUrl, String keycloakUrl, boolean debug = false){
    super(context, kongUrl, keycloakUrl, debug)
  }

  Applications(Kong kong){
    this(kong.context, kong.kongUrl, kong.keycloak, kong.getDebug())
  }

  String registerApplication(def jsonAppDefinition) {
    logger.info("Register application \"${jsonAppDefinition.id}\"")

    Map<String, String> headers = getMasterHttpHeaders()

    def response = restClient.post(generateUrl("/applications"), jsonAppDefinition, headers, [201, 409])
    String contentStr = response.body.toString()
    Map content = response.body as Map

    if (response.responseCode == 409) {
      if (contentStr.contains("Application descriptor already created")) {
        logger.info("""
          Application \"${jsonAppDefinition.id}\" already exists
          Status: ${response.responseCode}
          Response content:
          ${contentStr}""")

        // Check twice
        def app = getRegisteredApplication(jsonAppDefinition.id as String)
        return app.id
      } else {
        logger.error("""
          The application registering result
          Status: ${response.responseCode}
          Response content:
          ${contentStr}""")

        throw new Exception("Build failed: " + contentStr)
      }
    }

    logger.info("""
      Info on the newly created application \"${jsonAppDefinition.id}\"
      Status: ${response.responseCode}
      Response content:
      ${content.toString()}""")

    return content.id
  }

  def getRegisteredApplication(String appId, boolean fullInfo = false){
    List apps = getRegisteredApplications("id==$appId", fullInfo)

    if (apps.size() == 0)
      throw new Exception("Application is not registered")

    return apps[0]
  }

  boolean isApplicationRegistered(String appId) {
    return getRegisteredApplications("id==$appId").size() > 0
  }

  List getRegisteredApplications(String query = "", boolean fullInfo = false, int limit = 500){
    logger.info("Get application(s)${query ? " with query=${query}" : ""}...")

    Map<String, String> headers = getMasterHttpHeaders()

    String url = generateUrl("/applications?${query ? "query=$query" : ""}&full=$fullInfo&limit=$limit")

    Map response = restClient.get(url, headers).body as Map

    if (response.totalRecords > 0) {
      logger.debug("Found application(s): ${response.applicationDescriptors.collect({it['id']})}")
      return response.applicationDescriptors as List
    } else {
      logger.debug("By the url ${url} registered application(s) not found")
      logger.debug("HTTP response is: ${response}")

      return []
    }
  }

  Applications registerModules(def jsonModuleList) {
    logger.info("Register module list...")

    Map<String, String> headers = getMasterHttpHeaders()

    restClient.post(generateUrl("/modules/discovery"), jsonModuleList, headers)

    return this
  }

  /**
   * Get Existing Module Discovery by its ID
   * @param module FolioModule object to discover
   * @return Module Discovery Information as Map
   */
  Map getModuleDiscovery(FolioModule module) {
    Map<String, String> headers = getMasterHttpHeaders()

    // URL for GET request
    String url = generateUrl("/modules/${module.name}-${module.version}/discovery")

    logger.info("Getting Module Discovery for new module version...")

    def response = restClient.get(url, headers, [200, 404])
    String contentStr = response['body'].toString()

    if (response['responseCode'] == 404) {
      if (contentStr.contains("Unable to find discovery of the module with id")) {
        logger.info("""
          Module \"${module.name}-${module.version}\" not found in environment
          Status: ${response['responseCode']}
          Response content:
          ${contentStr}
        """.stripIndent())

        throw new RequestException(contentStr, response['responseCode'] as int)
      }
    }

    logger.info("Module Discovery Info is provided for ${module.name}-${module.version}.")

    return response as Map
  }

  /**
   * Create New Module Discovery for Application
   * @param module FolioModule object to discover
   */
  void createModuleDiscovery(FolioModule module) {
    Map<String, String> headers = getMasterHttpHeaders()

    // URL for POST request
    String url = generateUrl("/modules/${module.name}-${module.version}/discovery")

    // Request Body for POST request
    Map requestBody = [
      'location': "http://${module.name}:8082",
      'id': "${module.name}-${module.version}",
      'name': module.name,
      'version': module.version
    ]

    logger.info("Performing Module Discovery for new module version...")

    def response = restClient.post(url, requestBody, headers).body

    logger.info("New Module Discovery is created for ${module.name}-${module.version}.")
  }

  /**
   * Upgrade Applications (switch to new version) on Tenant
   * @param tenant EurekaTenant object to upgrade application for
   * @param appsToEnableMap Map<AppName, AppID> of Applications to enable on Tenant
   */
  void upgradeTenantApplication(EurekaTenant tenant, Map<String, String> appsToEnableMap) {
    // Get Authorization Headers for Master Tenant from Keycloak
    Map<String, String> headers = getMasterHttpHeaders()

    // URL for PUT request
    String url = generateUrl("/entitlements")

    // Request Body for PUT request
    Map requestBody = [
      'tenantId': tenant.uuid,
      'applications': appsToEnableMap.values()
    ]

    logger.info("Performing Application Upgrade for \"${tenant.tenantName}\" Tenant...")

    restClient.put(url, requestBody, headers)

    logger.info("We've successfully upgraded Application for \"${tenant.tenantName}\" Tenant.")
  }

  /**
   * Delete Module Discovery for Registered Application
   * @param moduleId
   */
  void deleteModuleDiscovery(String moduleId) {
    Map<String, String> headers = getMasterHttpHeaders()

    // URL for DELETE request
    String url = generateUrl("/modules/${moduleId}/discovery")

    logger.info("Deleting Module Discovery for ${moduleId} module version...")

    restClient.delete(url, headers)

    logger.info("Module Discovery is deleted for ${moduleId}.")
  }

  /**
   * Delete Registered Application
   * @param appId
   */
  void deleteRegisteredApplication(String appId) {
    logger.info("Delete registered application ${appId} ...")

    Map<String, String> headers = getMasterHttpHeaders()

    restClient.delete(generateUrl("/applications/${appId}"), headers)

    logger.info("Registered Application ${appId} is deleted.")
  }

  /**
   * Search Module Discovery by query
   * @param query search query (leave empty for all)
   * @param limit limit of search results (default 300)
   * @return Map of Module Discoveries
   */
  Map searchModuleDiscovery(String query = '', int limit = 300) {
    logger.info("Get Module Discoveries${query ? " with query=${query}" : ""}...")

    Map<String, String> headers = getMasterHttpHeaders()

    String url = generateUrl("/modules/discovery?${query ? "query=$query" : ""}&limit=$limit")

    Map response = restClient.get(url, headers).body as Map

    logger.info("Got Module Discoveries successfully.")

    return response
  }

  @NonCPS
  static Applications get(Kong kong){
    return new Applications(kong)
  }
}
