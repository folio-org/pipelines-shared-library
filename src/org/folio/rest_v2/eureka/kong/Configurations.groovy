package org.folio.rest_v2.eureka.kong

import com.cloudbees.groovy.cps.NonCPS
import groovy.json.JsonOutput
import groovy.text.GStringTemplateEngine
import org.folio.models.EurekaTenant
import org.folio.models.LdpConfig
import org.folio.rest_v2.Constants
import org.folio.rest_v2.eureka.Keycloak
import org.folio.rest_v2.eureka.Kong
import org.folio.utilities.RequestException

import java.net.URLEncoder
import java.util.UUID

/**
 * The Configurations class is responsible for managing various configurations
 * related to a tenant such as RMAPI configuration, WorldCat, SMTP settings, and password reset link.
 */
class Configurations extends Kong {

  static final String KB_CREDENTIALS_ID = '80898dee-449f-44dd-9c8e-37d5eb469b1d'

  static final String COPYCAT_PROFILE_ID = 'f26df83c-aa25-40b6-876e-96852c3d4fd4'

  /** mod-settings scope and key read by mod-reporting/ui-ldp for the Reporting database connection. */
  static final String LDP_SETTINGS_SCOPE = 'ui-ldp.admin'
  static final String LDP_SETTINGS_KEY = 'dbinfo'

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
    try {
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
    } catch (Exception e) {
      logger.warning("Setup of KB failed with error ${e.getMessage()}")
    }
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

  /**
   * Sets the base-url for the specified tenant via PUT /base-url endpoint.
   * This endpoint was introduced after the Sunflower release and should not be called for it.
   *
   * @param tenant The tenant for which to set the base-url.
   */
  Configurations setBaseUrl(EurekaTenant tenant) {
    if (!tenant.okapiConfig?.resetPasswordLink)
      return this

    logger.info("Set base-url on tenant ${tenant.tenantId} with ${tenant.uuid}...")

    Map<String, String> headers = getTenantHttpHeaders(tenant)
    String url = generateUrl("/base-url")
    Map body = [baseUrl: tenant.okapiConfig.resetPasswordLink]

    restClient.put(url, body, headers)
    logger.info("base-url on tenant ${tenant.tenantId} with ${tenant.uuid} was set successfully")

    return this
  }

  /**
   * Stores the Reporting app database configuration (scope "ui-ldp.admin", key "dbinfo")
   * in mod-settings. mod-reporting reads this record on every request, so the database
   * connection no longer has to be entered manually in Settings -> Database configuration.
   *
   * @param tenant The tenant for which the reporting database configuration is to be set.
   */
  Configurations setLdpDbSettings(EurekaTenant tenant) {
    LdpConfig ldpConfig = tenant.okapiConfig?.ldpConfig
    if (!ldpConfig) {
      logger.info("LDP configuration is not defined for tenant ${tenant.tenantId}. " +
        "Skipping '${LDP_SETTINGS_SCOPE}/${LDP_SETTINGS_KEY}' setting")
      return this
    }

    String value = JsonOutput.toJson([
      url : "jdbc:postgresql://${ldpConfig.dbHost}:${ldpConfig.dbPort ?: '5432'}/${ldpConfig.ldpDbName}".toString(),
      user: ldpConfig.ldpDbUserName,
      pass: ldpConfig.ldpDbUserPassword
    ])

    try {
      upsertSettingsEntry(tenant, LDP_SETTINGS_SCOPE, LDP_SETTINGS_KEY, value)
    } catch (RequestException e) {
      logger.error("Setting '${LDP_SETTINGS_SCOPE}/${LDP_SETTINGS_KEY}' " +
        "on tenant ${tenant.tenantId} can not be applied: ${e.getMessage()}. Status code: ${e.statusCode}")
    }

    return this
  }

  /**
   * Retrieves a single mod-settings entry identified by scope and key.
   *
   * @param tenant The tenant the settings entry belongs to.
   * @param scope  The settings scope.
   * @param key    The settings key within the scope.
   * @return The settings entry as a Map, or null when it does not exist.
   */
  Map getSettingsEntry(EurekaTenant tenant, String scope, String key) {
    logger.info("Get '${scope}/${key}' setting on tenant ${tenant.tenantId}...")

    // mod-settings enforces X-Okapi-Permissions on /settings/*, and the sidecar only populates
    // that header when the request carries a user token (Keycloak 'user_id' claim). A
    // client-credentials token has no user context, so the call is rejected with 400/401.
    Map<String, String> headers = getTenantUserHttpHeaders(tenant)
    String query = URLEncoder.encode("scope==\"${scope}\" and key==\"${key}\"", 'UTF-8').replace('+', '%20')
    String url = generateUrl("/settings/entries?query=${query}&limit=1")

    def response = restClient.get(url, headers, [200, 404])
    if (response.responseCode == 404) {
      return null
    }

    List entries = response.body['items'] as List
    return entries ? entries.first() as Map : null
  }

  /**
   * Creates or updates a mod-settings entry, so the call is safe to repeat on every tenant configuration run.
   *
   * @param tenant The tenant the settings entry belongs to.
   * @param scope  The settings scope.
   * @param key    The settings key within the scope.
   * @param value  The settings value.
   */
  Configurations upsertSettingsEntry(EurekaTenant tenant, String scope, String key, Object value) {
    // Same user-context requirement as getSettingsEntry: mod-settings rejects requests
    // without X-Okapi-Permissions, which the sidecar derives from a user token only.
    Map<String, String> headers = getTenantUserHttpHeaders(tenant)
    Map existing = getSettingsEntry(tenant, scope, key)

    if (existing) {
      restClient.put(generateUrl("/settings/entries/${existing['id']}"),
        [id: existing['id'], scope: scope, key: key, value: value], headers)
      logger.info("Setting '${scope}/${key}' on tenant ${tenant.tenantId} was updated")
    } else {
      // mod-settings rejects POST without an id ("Entry must have an id"), so one is generated here.
      restClient.post(generateUrl('/settings/entries'),
        [id: UUID.randomUUID().toString(), scope: scope, key: key, value: value], headers)
      logger.info("Setting '${scope}/${key}' on tenant ${tenant.tenantId} was created")
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

    Map<String, String> headers = getTenantUserHttpHeaders(tenant)
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
