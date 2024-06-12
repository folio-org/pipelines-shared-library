package org.folio.models

/**
 * The SmtpConfig class holds configuration details needed for sending emails through an SMTP server.
 */
class SmtpConfig {

  /**
   * Host name or IP address of the SMTP server.
   */
  String host

  /**
   * Port number of the SMTP server.
   */
  String port

  /**
   * Username for SMTP server authentication.
   */
  String username

  /**
   * Password for SMTP server authentication.
   */
  String password

  /**
   * Email address used in the 'from' field of the email.
   */
  String from

  /**
   * Constructor for creating an instance of SmtpConfig class.
   * Initializes host, port, username, password, and from.
   *
   * @param host The host name or IP address of the SMTP server.
   * @param port The port number of the SMTP server.
   * @param username The username for SMTP server authentication.
   * @param password The password for SMTP server authentication.
   * @param from The email address used in the 'from' field of the email.
   */
  SmtpConfig(String host, String port, String username, String password, String from) {
    this.host = host
    this.port = port
    this.username = username
    this.password = password
    this.from = from
  }
}
