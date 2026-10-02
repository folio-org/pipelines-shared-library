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
   * Two-step approach to avoid fetching huge full descriptors for every matching application:
   *   1. Query without {@code full=true} to get lightweight records (id + version only) and
   *      pick the one with the highest SNAPSHOT build number.
   *   2. Fetch the single winning application's full descriptor.
   *
   * @param moduleName Module name without version suffix (e.g. "mod-inventory")
   * @return The latest matching {@link Application}, or {@code null} if none found.
   */
  Application getLatestApplicationByModuleName(String moduleName) {
    logger.info("Searching FAR for the latest application containing module '${moduleName}'...")

    // Step 1: lightweight query — no full=true, so each record is just {id, name, version}.
    // The CQL wildcard matches any application whose module ids start with "<moduleName>-".
    String url = generateUrl("/applications?query=modules.id==${moduleName}*&limit=500")
    Map response = restClient.get(url, getDefaultHeaders()).body as Map

    int total = (response.totalRecords ?: 0) as int
    if (total == 0) {
      logger.warning("FAR returned no applications for module query '${moduleName}*'.")
      return null
    }

    List<Map> slim = response.applicationDescriptors as List<Map>

    // Pick the record with the highest SNAPSHOT build number.
    // We don't filter by module name here because without full=true the response contains
    // only {id, name, version} — no modules list. Exact-module verification happens in step 2
    // after we fetch only the single winning full descriptor.
    Map latest = slim.max { Map d ->
      String ver = (d.version ?: '0.0.0') as String
      if (ver.contains('SNAPSHOT.')) {
        try { return Long.parseLong(ver.split('SNAPSHOT\\.')[1]) }
        catch (ignored) { return 0L }
      }
      return 0L
    }

    String latestId = latest.id as String
    logger.info("Latest FAR application candidate for module '${moduleName}': '${latestId}'. " +
      "Fetching full descriptor...")

    // Step 2: fetch only the single winning descriptor with full=true.
    Map fullDescriptor = getApplicationDescriptor(latestId, true)

    // Verify the full descriptor actually contains the exact module (not a super-module false positive).
    boolean hasModule =
      (fullDescriptor.modules ?: []).any { m -> (m.id as String).startsWith("${moduleName}-") } ||
      (fullDescriptor.uiModules ?: []).any { m -> (m.id as String).startsWith("${moduleName}-") }

    if (!hasModule) {
      logger.warning("Top candidate '${latestId}' does not contain module '${moduleName}' " +
        "(super-module false positive). FAR fallback cannot proceed.")
      return null
    }

    logger.info("Confirmed: '${latestId}' contains module '${moduleName}'. Using as FAR fallback application.")
    return new Application().withDescriptor(fullDescriptor)
  }
}
