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

    boolean isRmapiConfigExist(OkapiTenant tenant) {
        String kbCredentialsId = '80898dee-449f-44dd-9c8e-37d5eb469b1d'
        String url = generateUrl("/eholdings/kb-credentials/${kbCredentialsId}")
        Map<String, String> headers = getAuthorizedHeaders(tenant)
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
    void setRmapiConfig(OkapiTenant tenant) {
        String kbCredentialsId = '80898dee-449f-44dd-9c8e-37d5eb469b1d'
        if (!tenant.okapiConfig?.kbApiKey) {
            logger.warning("KB Api key not set. Skipping.")
            return
        }
        if (!isRmapiConfigExist(tenant)) {
            logger.warning("KB Credentials configuration not exists")
            return
        }
        String url = generateUrl("/eholdings/kb-credentials/${kbCredentialsId}")
        Map<String, String> headers = getAuthorizedHeaders(tenant)
        headers["Content-Type"] = "application/vnd.api+json"
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
    boolean isWorldcatExists(OkapiTenant tenant) {
        String copycatProfileId = 'f26df83c-aa25-40b6-876e-96852c3d4fd4'
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
        String copycatProfileId = 'f26df83c-aa25-40b6-876e-96852c3d4fd4'
        if (!isWorldcatExists(tenant)) {
            logger.warning("Worldcat's id not exits, reference data not performed")
            return
        }

        String url = generateUrl("/copycat/profiles/${copycatProfileId}")
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
        if (!tenant.okapiConfig?.smtp) {
            logger.warning("SMTP configuration not provided")
            return
        }
        String url = generateUrl("/configurations/entries")
        Map<String, String> headers = getAuthorizedHeaders(tenant)
        Map binding = [email_smtp_host: tenant.okapiConfig.smtp.host,
                       email_smtp_port: tenant.okapiConfig.smtp.port,
                       email_username : tenant.okapiConfig.smtp.username,
                       email_password : tenant.okapiConfig.smtp.password,
                       email_from     : tenant.okapiConfig.smtp.from]
        Constants.CONFIGURATIONS.smtpConfig.each {

            def content = steps.readFile file: tools.copyResourceFileToCurrentDirectory("okapi/configurations/" + it)
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
        if (!tenant.okapiConfig?.resetPasswordLink) {
            logger.warning("Reset password link configuration not provided")
            return
        }
        String url = generateUrl("/configurations/entries")
        Map<String, String> headers = getAuthorizedHeaders(tenant)

        Map binding = [stripes_url: tenant.okapiConfig.resetPasswordLink]
        Constants.CONFIGURATIONS.resetPassword.each {
            def content = steps.readFile file: tools.copyResourceFileToCurrentDirectory("okapi/configurations/" + it)
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
