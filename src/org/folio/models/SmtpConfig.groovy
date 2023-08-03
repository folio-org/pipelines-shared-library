package org.folio.models

/**
 * The SmtpConfig class holds configuration details needed for sending emails through an SMTP server.
 */
class SmtpConfig {
    String host
    String port
    String username
    String password
    String from

    SmtpConfig(String host, String port, String username, String password, String from) {
        this.host = host
        this.port = port
        this.username = username
        this.password = password
        this.from = from
    }
}
