package org.folio.far

import org.folio.models.application.Application
import org.folio.models.application.ApplicationList
import org.folio.rest_v2.eureka.Base

class Far extends Base {
  static final String FAR_URL = "https://far.ci.folio.org"

  Far(def context, boolean debug = false) {
    super(context, debug)
  }

  static Map<String, String> getDefaultHeaders() {
    return ["Content-Type": "application/json"]
  }

  static String generateUrl(String path) {
    "${FAR_URL}${path}"
  }

  /**
   * Get application descriptor by ID from FAR
   * @param appId Application ID (e.g., "app-platform-minimal-1.0.0-SNAPSHOT.123")
   * @param fullInfo Include full module information
   * @return Application descriptor map
   */
  Map getApplicationDescriptor(String appId, boolean fullInfo = true) {
    logger.info("Fetching application ${appId} from FAR...")
    String url = generateUrl("/applications?query=id==${appId}&full=${fullInfo}")
    Map response = restClient.get(url, getDefaultHeaders()).body as Map

    if (response.totalRecords == 0) {
      throw new Exception("Application ${appId} not found in FAR")
    }

    if (response.totalRecords > 1) {
      throw new Exception("Multiple applications found for ID ${appId} in FAR")
    }

    return response.applicationDescriptors[0] as Map
  }

  /**
   * Fetch application descriptors by IDs and return as ApplicationList
   * @param appIds List of application IDs (e.g., ["app-platform-minimal-1.0.0-SNAPSHOT.123"])
   * @return ApplicationList with Application objects containing modules
   */
  ApplicationList getApplicationsByIds(List<String> appIds) {
    logger.info("Fetching applications from FAR: ${appIds}")
    ApplicationList apps = new ApplicationList()

    appIds.each { appId ->
      Map descriptor = getApplicationDescriptor(appId, true)
      apps.add(new Application().withDescriptor(descriptor))
    }

    logger.info("Fetched ${apps.size()} applications from FAR")
    return apps
  }

  /**
   * Fetch the latest application from FAR that contains a given module.
   *
   * Uses a CQL wildcard query to find all applications whose module list includes
   * an entry whose id starts with {@code "<moduleName>-"}, then picks the one with
   * the highest SNAPSHOT build number (or the highest patch version for release apps).
   *
   * @param moduleName Module name without version suffix (e.g. "mod-inventory")
   * @return The latest matching {@link Application}, or {@code null} if none found.
   */
  Application getLatestApplicationByModuleName(String moduleName) {
    logger.info("Searching FAR for the latest application containing module '${moduleName}'...")

    // CQL wildcard: match any application whose module ids start with "<moduleName>-"
    // (e.g. "mod-inventory-1.2.3"). The trailing wildcard relies on standard FOLIO CQL.
    String url = generateUrl("/applications?query=modules.id==${moduleName}*&full=true&limit=500")
    Map response = restClient.get(url, getDefaultHeaders()).body as Map

    int total = (response.totalRecords ?: 0) as int
    if (total == 0) {
      logger.warning("FAR returned no applications for module query '${moduleName}*'.")
      return null
    }

    List<Map> descriptors = response.applicationDescriptors as List<Map>

    // Client-side guard: the CQL wildcard can match super-modules
    // (e.g. "mod-inventory-storage-*" when searching for "mod-inventory").
    // Keep only descriptors where at least one module id starts with "${moduleName}-".
    List<Map> matching = descriptors.findAll { Map descriptor ->
      (descriptor.modules ?: []).any { m -> (m.id as String).startsWith("${moduleName}-") } ||
      (descriptor.uiModules ?: []).any { m -> (m.id as String).startsWith("${moduleName}-") }
    }

    if (!matching) {
      logger.warning("FAR returned ${total} record(s) but none contained an exact match for " +
        "module '${moduleName}' (checked modules.id prefix '${moduleName}-').")
      return null
    }

    // Pick the application with the highest SNAPSHOT build number.
    // For release versions the build number is treated as 0.
    Map latestDescriptor = matching.max { Map descriptor ->
      String ver = (descriptor.version ?: '0.0.0') as String
      if (ver.contains('SNAPSHOT.')) {
        try { return Long.parseLong(ver.split('SNAPSHOT\\.')[1]) }
        catch (ignored) { return 0L }
      }
      return 0L
    }

    logger.info("Latest FAR application for module '${moduleName}': '${latestDescriptor.id}'")
    return new Application().withDescriptor(latestDescriptor)
  }
}
