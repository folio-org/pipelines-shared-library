package org.folio.models

/**
 * The OkapiConfig class holds configuration details needed for the Okapi system.
 */
class OkapiConfig {

  /** Link used for resetting user password. */
  String resetPasswordLink

  /** Knowledge base API key for accessing external data. */
  String kbApiKey

  /** SMTP configuration object for managing email services. */
  SmtpConfig smtp

  /**
   * Default constructor for creating an instance of the OkapiConfig class.
   * Initializes resetPasswordLink, kbApiKey, and smtp properties.
   */
  OkapiConfig() {
    this.resetPasswordLink
    this.kbApiKey
    this.smtp
  }

  /**
   * Sets the reset password link for the OkapiConfig instance.
   *
   * @param link the link for resetting password
   * @return the OkapiConfig instance with the reset password link set
   */
  OkapiConfig withResetPasswordLink(String link) {
    this.resetPasswordLink = link
    return this
  }

  /**
   * Sets the knowledge base API key for the OkapiConfig instance.
   *
   * @param key the knowledge base API key
   * @return the OkapiConfig instance with the knowledge base API key set
   */
  OkapiConfig withKbApiKey(String key) {
    this.kbApiKey = key
    return this
  }

  /**
   * Sets the SMTP configuration for the OkapiConfig instance.
   *
   * @param config the SMTP configuration
   * @return the OkapiConfig instance with the SMTP configuration set
   */
  OkapiConfig withSmtp(SmtpConfig config) {
    this.smtp = config
    return this
  }
}
