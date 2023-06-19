package org.folio.models

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
