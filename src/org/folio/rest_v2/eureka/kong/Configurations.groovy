package org.folio.rest_v2.eureka.kong

import com.cloudbees.groovy.cps.NonCPS
import groovy.text.GStringTemplateEngine
import org.folio.models.EurekaTenant
import org.folio.rest_v2.Constants
import org.folio.rest_v2.eureka.Keycloak
import org.folio.rest_v2.eureka.Kong
import org.folio.utilities.RequestException

/**
 * The Configurations class is responsible for managing various configurations
 * related to a tenant such as RMAPI configuration, WorldCat, SMTP settings, and password reset link.
 */
class Configurations extends Kong {

  static final String KB_CREDENTIALS_ID = '80898dee-449f-44dd-9c8e-37d5eb469b1d'

  static final String COPYCAT_PROFILE_ID = 'f26df83c-aa25-40b6-876e-96852c3d4fd4'

  Configurations(def context, String kongUrl, Keycloak keycloak, boolean debug = false) {
    super(context, kongUrl, keycloak, debug)
  }

  Configurations(def context, String kongUrl, String keycloakUrl, boolean debug = false) {
    super(context, kongUrl, keycloakUrl, debug)
  }

  Configurations(Kong kong) {
    this(kong.context, kong.kongUrl, kong.keycloak, kong.getDebug())
  }

  boolean isRmapiConfigExist(EurekaTenant tenant) {
    String url = generateUrl("/eholdings/kb-credentials/${KB_CREDENTIALS_ID}")

    Map<String, String> headers = getTenantHttpHeaders(tenant)

    try {
      restClient.get(url, headers)

      return true
    } catch (RequestException e) {
      logger.warning(e.getMessage())

      return false
    }
  }

  /**
   * Sets the RMAPI configuration for the specified tenant.
   *
   * @param tenant The tenant for which the configuration is to be set.
   */
  void setRmapiConfig(EurekaTenant tenant) {
    if (!tenant.okapiConfig?.kbApiKey) {
      logger.warning("KB Api key not set. Skipping.")
      return
    }
    if (!isRmapiConfigExist(tenant)) {
      logger.warning("KB Credentials configuration not exists")
      return
    }
    String url = generateUrl("/eholdings/kb-credentials/${KB_CREDENTIALS_ID}")
    Map<String, String> headers = getTenantHttpHeaders(tenant, true)
    headers["Content-Type"] = "application/vnd.api+json"
    headers["X-Okapi-User-Id"] = tenant.getAdminUser().uuid
    Map body = [data: [type      : "kbCredentials",
                       attributes: [name      : "Knowledge Base",
                                    apiKey    : tenant.okapiConfig.kbApiKey,
                                    url       : Constants.KB_API_URL,
                                    customerId: Constants.KB_CUSTOMER_ID]

    ]]

    logger.info("Setting rmapi key")
    restClient.put(url, body, headers)
    logger.info("KB rmapi key successfully set")
  }

  /**
   * Checks if Worldcat is exists for the specified tenant.
   *
   * @param tenant The tenant for which to check the Worldcat availability.
   * @return boolean value indicating if Worldcat is available.
   */
  boolean isWorldcatExists(EurekaTenant tenant) {
    String url = generateUrl("/copycat/profiles/${COPYCAT_PROFILE_ID}")

    Map<String, String> headers = getTenantHttpHeaders(tenant)

    try {
      restClient.get(url, headers)
      logger.info("Worldcat exists")
      return true
    } catch (RequestException e) {
      logger.info("Worldcat - copycat/profiles/${COPYCAT_PROFILE_ID} is not exists")
      return false
    }
  }

  /**
   * Sets the Worldcat configuration for the specified tenant.
   *
   * @param tenant The tenant for which the configuration is to be set.
   */
  void setWorldcat(EurekaTenant tenant) {
    if (!isWorldcatExists(tenant)) {
      logger.warning("Worldcat's id not exits, reference data not performed")
      return
    }

    String url = generateUrl("/copycat/profiles/${COPYCAT_PROFILE_ID}")
    Map<String, String> headers = getTenantHttpHeaders(tenant)
    Map body = Constants.WORLDCAT

    restClient.put(url, body, headers)

    logger.info("Worldcat successfully set")
  }

  /**
   * Sets the SMTP settings for the specified tenant.
   *
   * @param tenant The tenant for which the SMTP settings are to be set.
   */
  Configurations setSmtp(EurekaTenant tenant) {
    if (!tenant.okapiConfig?.smtp)
      return this

    logger.info("Add SMTP configuration on tenant ${tenant.tenantId} with ${tenant.uuid}...")

    Map binding = [email_smtp_host: tenant.okapiConfig.smtp.host,
                   email_smtp_port: tenant.okapiConfig.smtp.port,
                   email_username : tenant.okapiConfig.smtp.username,
                   email_password : tenant.okapiConfig.smtp.password,
                   email_from     : tenant.okapiConfig.smtp.from]

    Constants.CONFIGURATIONS.smtpConfig.each { config ->
      def content = context.readFile file: tools.copyResourceFileToCurrentDirectory("okapi/configurations/" + config)
      String entries = new GStringTemplateEngine().createTemplate(content).make(binding).toString()

      setConfigurationEntries(tenant, entries)
    }

    return this
  }

  /**
   * Sets the reset password link for the specified tenant.
   *
   * @param tenant The tenant for which to set reset password link.
   */
  Configurations setResetPasswordLink(EurekaTenant tenant) {
    if (!tenant.okapiConfig?.resetPasswordLink)
      return this

    logger.info("Add reset password configuration on tenant ${tenant.tenantId} with ${tenant.uuid}...")

    Map binding = [stripes_url: tenant.okapiConfig.resetPasswordLink]

    Constants.CONFIGURATIONS.resetPassword.each { config ->
      def content = context.readFile file: tools.copyResourceFileToCurrentDirectory("okapi/configurations/" + config)
      String entries = new GStringTemplateEngine().createTemplate(content).make(binding).toString()

      setConfigurationEntries(tenant, entries) {
        String entryId = getUsersConfigurationEntries(tenant).find { entry -> entry.value ==~ /http.*/ }.id
        deleteConfigurationEntries(tenant, entryId)

        setConfigurationEntries(tenant, entries)
      }
    }

    return this
  }

  Map getUsersConfigurationEntries(EurekaTenant tenant) {
    return getConfigurationEntries(tenant, "configs=module=USERSBL")
  }

  Map getConfigurationEntries(EurekaTenant tenant, String query = "") {
    logger.info("Get configuration entries on tenant ${tenant.tenantId} with ${tenant.uuid}${query ? " with query=${query}" : ""}...")

    Map<String, String> headers = getTenantHttpHeaders(tenant)

    String url = generateUrl("/configurations/entries${query ? "?query=${query}" : ""}")

    return restClient.get(url, headers).body.configs
  }

  Configurations setConfigurationEntries(EurekaTenant tenant, String entries = null, Closure existAction = {}) {
    if (!entries)
      return this

    logger.info("""Set configuration's entires on tenant ${tenant.tenantId} with ${tenant.uuid} and the following entries
                  $entries""")

    Map<String, String> headers = getTenantHttpHeaders(tenant)
    String url = generateUrl("/configurations/entries")

    def response = restClient.post(url, entries, headers, [201, 422])
    String contentStr = response.body.toString()

    if (response.responseCode == 422) {
      logger.warning("""
          Configuration entries already presents on tenant, no actions needed..
          Status: ${response.responseCode}
          Response content:
          ${contentStr}""")

      existAction.run()
    } else
      logger.info("Setting configuration entries on tenant ${tenant.tenantId} with ${tenant.uuid} were finished successfully")

    return this
  }

  Configurations deleteConfigurationEntries(EurekaTenant tenant, String entryId) {
    if (!entryId)
      return this

    logger.info("Delete configuration's entires $entryId on tenant ${tenant.tenantId} with ${tenant.uuid}...")

    Map<String, String> headers = getTenantHttpHeaders(tenant)
    String url = generateUrl("/configurations/entries/$entryId")

    restClient.delete(url, headers)

    logger.info("Deleting configuration entries $entryId on tenant ${tenant.tenantId} with ${tenant.uuid} were finished successfully")

    return this
  }

  /**
   * Retrieves a list of timer descriptors for the specified tenant.
   *
   * @param tenant The tenant for which to retrieve timer descriptors.
   * @param limit  The maximum number of timer descriptors to retrieve (default is 500).
   * @return A list of timer descriptors.
   */
  List getTimerDescriptors(EurekaTenant tenant, String limit = '500') {
    Map<String, String> headers = getTenantUserHttpHeaders(tenant)
    String url = generateUrl("/scheduler/timers?limit=${limit}")

    logger.info("Getting timer descriptors on tenant ${tenant.tenantId}...")
    List timerDescriptors = restClient.get(url, headers, [200]).body['timerDescriptors']
    logger.info("Timer descriptors (${timerDescriptors.size()}) on tenant ${tenant.tenantId} were obtained successfully")
    return timerDescriptors
  }

  /**
   * Deletes a specific timer for the specified tenant.
   *
   * @param tenant  The tenant for which the timer is to be deleted.
   * @param timerId The ID of the timer to delete.
   */
  void deleteTimer(EurekaTenant tenant, String timerId) {
    Map<String, String> headers = getTenantUserHttpHeaders(tenant)
    String url = generateUrl("/scheduler/timers/${timerId}")

    logger.info("Deleting timer ${timerId} on tenant ${tenant.tenantId} ...")
    restClient.delete(url, headers, [204])
    logger.info("Timer ${timerId} on tenant ${tenant.tenantId} was deleted successfully")
  }

  /**
   * Creates a new timer for the specified tenant.
   *
   * @param tenant          The tenant for which the timer is to be created.
   * @param timerDescriptor A map containing the timer's configuration details.
   * @return A map containing the details of the created timer.
   */
  Map createTimer(EurekaTenant tenant, Map timerDescriptor) {
    if (!timerDescriptor) {
      logger.warning("Timer descriptor is null or empty. Cannot create timer.")
      return null
    }

    Map<String, String> headers = getTenantHttpHeaders(tenant)
    String url = generateUrl('/scheduler/timers')

    logger.info("Creating timer for tenant ${tenant.tenantId}...")
    Map response = restClient.post(url, timerDescriptor, headers, [201]).body
    logger.info("Timer for tenant ${tenant.tenantId} was created successfully with ID: ${response.id}")

    return response
  }

  /**
   * Recreates a timer by deleting the existing one and creating a new timer with the provided descriptor.
   *
   * @param tenant          The tenant for which the timer is to be recreated.
   * @param prevTimerId     The ID of the timer to delete before creating a new one.
   * @param timerDescriptor A map containing the new timer's configuration details.
   * @return A map containing the details of the newly created timer.
   */
  Map recreateTimer(EurekaTenant tenant, String prevTimerId, Map timerDescriptor) {
    logger.info("Recreating timer ${prevTimerId} for tenant ${tenant.tenantId}...")
    deleteTimer(tenant, prevTimerId)
    Map newTimerDescriptor = createTimer(tenant, timerDescriptor)
    logger.info("Timer ${prevTimerId} for tenant ${tenant.tenantId} was recreated successfully with new ID: " +
      "${newTimerDescriptor.id} and moduleId: ${newTimerDescriptor.moduleId}")
    return newTimerDescriptor
  }


  @NonCPS
  static Configurations get(Kong kong) {
    return new Configurations(kong)
  }
}
