package org.folio.models

/**
 * The OkapiConfig class holds configuration details needed for the Okapi system.
 */
class OkapiConfig {
    String resetPasswordLink
    String kbApiKey
    SmtpConfig smtp

    OkapiConfig() {
        this.resetPasswordLink
        this.kbApiKey
        this.smtp
    }

    OkapiConfig withResetPasswordLink(String link){
        this.resetPasswordLink = link
        return this
    }

    OkapiConfig withKbApiKey(String key){
        this.kbApiKey = key
        return this
    }

    OkapiConfig withSmtp (SmtpConfig config){
        this.smtp = config
        return this
    }
}
