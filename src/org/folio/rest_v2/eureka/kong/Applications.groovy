package org.folio.rest_v2.eureka.kong

import com.cloudbees.groovy.cps.NonCPS
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
    return getRegisteredApplications("id==$appId", fullInfo)[0]
  }

  boolean isApplicationRegistered(String appId) {
    return getRegisteredApplications("id==$appId").size() > 0
  }

  List getRegisteredApplications(String query = "", boolean fullInfo = false, int limit = 500){
    logger.info("Get application(s)${query ? " with query=${query}" : ""}...")

    Map<String, String> headers = getMasterHttpHeaders()

    String url = generateUrl("/applications?${query ? "query=$query" : ""}&fullInfo=$fullInfo&limit=$limit")

    Map response = restClient.get(url, headers).body as Map

    if (response.totalRecords > 0) {
      logger.debug("Found application(s): ${response.applicationDescriptors}")
      return response.applicationDescriptors as List
    } else {
      logger.debug("By the url ${url} registered application(s) not found")
      logger.debug("HTTP response is: ${response}")
      throw new Exception("Application(s) is(are) not registered")
    }
  }

  Applications registerModules(def jsonModuleList) {
    logger.info("Register module list...")

    Map<String, String> headers = getMasterHttpHeaders()

    restClient.post(generateUrl("/modules/discovery"), jsonModuleList, headers)

    return this
  }

  @NonCPS
  static Applications get(Kong kong){
    return new Applications(kong)
  }

  /**
   * Get List of Enabled Eureka Applications (Entitlements)
   * @param query CQL query
   * @param limit output records default 500
   * @return List of Entitled Eureka Applications
   */
  List getEnabledApplications(String query = "", int limit = 500){
    logger.info("Get Enabled Application(s)${query ? " with query=${query}" : ""}...")

    Map<String, String> headers = getMasterHttpHeaders()

    String url = generateUrl("/entitlements?${query ? "query=$query" : ""}&limit=$limit")

    Map response = restClient.get(url, headers).body as Map

    if (response.totalRecords > 0) {
      logger.debug("Found application(s): ${response.entitlements}")
      return response.entitlements as List
    } else {
      logger.debug("By the url ${url} enabled application(s) not found")
      logger.debug("HTTP response is: ${response}")
      throw new Exception("Application(s) is(are) not enabled")
    }
  }

  /**
   * Check if specific application is enabled
   * @param appId application id (e.g. app-platform-full-1.0.0-SNAPSHOT.176)
   * @return boolean true if application is enabled
   */
  boolean isApplicationEnabled(String appId) {
    return getEnabledApplications("applicationId=${appId}").size() > 0
  }

  /**
   * Get specific enabled application
   * @param appId application id (e.g. app-platform-full-1.0.0-SNAPSHOT.176)
   * @return Map with enabled application
   */
  Map getEnabledApplicationById(String appId){
    return getEnabledApplications("applicationId==$appId")[0]
  }
}
