package org.folio.rest_v2.eureka.kong

import com.cloudbees.groovy.cps.NonCPS
import org.folio.models.module.EurekaModule
import org.folio.models.module.FolioModule
import org.folio.rest_v2.eureka.Keycloak
import org.folio.rest_v2.eureka.Kong

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

  String registerApplication(Map jsonAppDefinition) {
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
        def app = getRegisteredApplicationDescriptors(jsonAppDefinition.id as String)
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

  def getRegisteredApplicationDescriptors(String appId, boolean fullInfo = false){
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

  /**
   * Delete registered application
   * @param appId
   * @return this instance of Applications
   */
  Applications deleteRegisteredApplication(String appId) {
    logger.info("Delete registered application ${appId} ...")

    Map<String, String> headers = getMasterHttpHeaders()

    restClient.delete(generateUrl("/applications/${appId}"), headers)

    logger.info("Registered Application ${appId} is deleted.")

    return this
  }

  /**
   * Register modules (discovery information)
   * @param modules list of modules to register
   * @param skipExists if true, skip already registered (409 response code) error
   * @return this instance of Applications
   */
  Applications registerModules(List<? extends FolioModule> modules, boolean skipExists = false) {
    logger.info("Register/discovery modules $modules ...")

    Map<String, String> headers = getMasterHttpHeaders()

    Map requestsBody = [
      "discovery": modules.collect { it.getDiscovery() }
    ]

    List validResponseCodes = skipExists ? [201, 409] : []
    def response = restClient.post(generateUrl("/modules/discovery"), requestsBody, headers, validResponseCodes)

    String contentStr = response.body.toString()
    Map content = response.body as Map

    if (response.responseCode == 409) {
      if (contentStr.contains("Module Discovery already exists")) {
        logger.info("""
          Given modules already exists
          Status: ${response.responseCode}
          Response content:
          ${contentStr}""")

        return this
      } else {
        logger.error("""
          The module registering result
          Status: ${response.responseCode}
          Response content:
          ${contentStr}""")

        throw new Exception("Build failed: " + contentStr)
      }
    }

    logger.info("""
      Modules successfully registered
      Status: ${response.responseCode}
      Response content:
      ${content.toString()}""")

    return this
  }

  /**
   * Check if module is registered
   * @param module
   * @return true if module is registered, false otherwise
   */
  boolean isModuleRegistered(EurekaModule module) {
    return getRegisteredModulesDiscovery("name==${module.name}").size() > 0
  }

  List<EurekaModule> getRegisteredModules(int limit = 500) {
    return getRegisteredModulesDiscovery("", limit)
      .collect { new EurekaModule().loadModuleDetails(it.id as String, 'enabled') }
  }

  /**
   * Get registered modules
   * @param query search query (leave empty for all)
   * @param limit limit of search results (default 500)
   * @return json with registered modules discovery information
   */
  List<Map> getRegisteredModulesDiscovery(String query = "", int limit = 500) {
    logger.info("Get registered modules${query ? " with query=${query}" : ""}...")

    Map<String, String> headers = getMasterHttpHeaders()

    String url = generateUrl("/modules/discovery?${query ? "query=${query}&limit=${limit}" : "limit=${limit}"}")

    Map response = restClient.get(url, headers).body as Map

    if (response.totalRecords > 0) {
      logger.debug("Found modules: ${response.discovery.collect({ it['id'] })}")
      return response.discovery as List
    } else {
      logger.debug("By the url ${url} registered modules not found")
      logger.debug("HTTP response is: ${response}")

      return []
    }
  }

  /**
   * Delete module registration (discovery information)
   * @param module
   * @return this instance of Applications
   */
  Applications deleteRegisteredModule(EurekaModule module) {
    logger.info("Deleting module registration (discovery information) for module ${module.getId()} ...")

    Map<String, String> headers = getMasterHttpHeaders()

    String url = generateUrl("/modules/${module.getId()}/discovery")

    restClient.delete(url, headers)

    logger.info("Module registration has been deleted for ${module.getId()}.")

    return this
  }

  @NonCPS
  static Applications get(Kong kong){
    return new Applications(kong)
  }
}
