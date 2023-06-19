package org.folio.rest_v2

import groovy.text.GStringTemplateEngine
import org.folio.models.OkapiTenant
import org.folio.utilities.RequestException

/**
 * The Configurations class is responsible for managing various configurations
 * related to a tenant such as RMAPI configuration, WorldCat, SMTP settings, and password reset link.
 */
class Configurations extends Authorization {

    Configurations(Object context, String okapiDomain, boolean debug = false) {
        super(context, okapiDomain, debug)
    }

    /**
     * Sets the RMAPI configuration for the specified tenant.
     *
     * @param tenant The tenant for which the configuration is to be set.
     */
    void setRmapiConfig(OkapiTenant tenant) {
        if (!tenant.kbApiKey) {
            logger.warning("KB Api key not set. Skipping.")
            return
        }
        String url = generateUrl("/eholdings/kb-credentials/80898dee-449f-44dd-9c8e-37d5eb469b1d")
        Map<String, String> headers = getAuthorizedHeaders(tenant)
        headers["Content-Type"] = "application/vnd.api+json"
        Map body = [data: [type      : "kbCredentials",
                           attributes: [name      : "Knowledge Base",
                                        apiKey    : tenant.kbApiKey,
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
    boolean checkWorldcat(OkapiTenant tenant) {
        String copycatProfileId = "f26df83c-aa25-40b6-876e-96852c3d4fd4"
        String url = generateUrl("/copycat/profiles/${copycatProfileId}")
        Map<String, String> headers = getAuthorizedHeaders(tenant)

        try {
            restClient.get(url, headers)
            logger.info("Worldcat exists")
            return true
        } catch (RequestException e) {
            logger.info("Worldcat - copycat/profiles/${copycatProfileId} is not exists")
            return false
        }
    }

    /**
     * Sets the Worldcat configuration for the specified tenant.
     *
     * @param tenant The tenant for which the configuration is to be set.
     */
    void setWorldcat(OkapiTenant tenant) {
        if (!checkWorldcat(tenant)) {
            logger.warning("Worldcat's id not exits, reference data not performed")
            return
        }

        String url = generateUrl("/copycat/profiles/f26df83c-aa25-40b6-876e-96852c3d4fd4")
        Map<String, String> headers = getAuthorizedHeaders(tenant)
        Map body = Constants.WORLDCAT

        restClient.put(url, body, headers)
        logger.info("Worldcat successfully set")
    }

    /**
     * Sets the SMTP settings for the specified tenant.
     *
     * @param tenant The tenant for which the SMTP settings are to be set.
     */
    void setSmtp(OkapiTenant tenant) {
        if (!tenant.smtpConfig) {
            logger.warning("SMTP configuration not provided")
            return
        }
        String url = generateUrl("/configurations/entries")
        Map<String, String> headers = getAuthorizedHeaders(tenant)
        Map binding = [email_smtp_host: tenant.smtpConfig.host,
                       email_smtp_port: tenant.smtpConfig.port,
                       email_username : tenant.smtpConfig.username,
                       email_password : tenant.smtpConfig.password,
                       email_from     : tenant.smtpConfig.from]
        Constants.CONFIGURATIONS.smtpConfig.each {
            tools.copyResourceFileToWorkspace("okapi/configurations/" + it)
            def content = steps.readFile it
            String body = new GStringTemplateEngine().createTemplate(content).make(binding).toString()
            try {
                restClient.post(url, body, headers)
            } catch (RequestException e) {
                if (e.statusCode == 422) {
                    logger.warning("Configuration already presented. ${e.getMessage()}")
                } else {
                    logger.error("Template ${it} can not be applied: ${e.getMessage()}. Status code: ${e.statusCode}")
                }
            }
        }
    }

    /**
     * Sets the reset password link for the specified tenant.
     *
     * @param tenant The tenant for which to set reset password link.
     */
    void setResetPasswordLink(OkapiTenant tenant) {
        String url = generateUrl("/configurations/entries")
        Map<String, String> headers = getAuthorizedHeaders(tenant)

        Map binding = [stripes_url: tenant.domains["ui"]]
        Constants.CONFIGURATIONS.resetPassword.each {
            tools.copyResourceFileToWorkspace("okapi/configurations/" + it)
            def content = steps.readFile it
            String body = new GStringTemplateEngine().createTemplate(content).make(binding).toString()
            try {
                restClient.post(url, body, headers)
            } catch (RequestException e) {
                if (e.statusCode == 422) {
                    logger.warning("Configuration already presented. ${e.getMessage()}")
                } else {
                    logger.error("Template ${it} can not be applied: ${e.getMessage()}. Status code: ${e.statusCode}")
                }
            }
        }
    }
}
