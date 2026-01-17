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

    logger.debug("Fetched application descriptor: ${response.applicationDescriptors[0]}")
    context.input(message: "Let's check raw descriptor")

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

      logger.debug("APP ${appId}: ${new Application().withDescriptor(descriptor)}")
      context.input(message: "Let's check application ${appId}")
    }

    logger.info("Fetched ${apps.size()} applications from FAR")
    return apps
  }
}
