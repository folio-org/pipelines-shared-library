package org.folio.rest_v2

import org.folio.models.OkapiTenant
import org.folio.models.OkapiUser
import org.folio.utilities.RequestException

/**
 * The Authorization class is responsible for various operations related to
 * user and tenant authorization. This includes generating URLs, headers, and
 * tokens, checking user credentials, setting user credentials, and logging in.
 */
class Authorization extends Common {

  /**
   * Initializes a new instance of the Authorization class.
   *
   * @param context The current context.
   * @param okapiDomain The domain for Okapi.
   * @param debug Debug flag indicating whether debugging is enabled.
   */
  Authorization(Object context, String okapiDomain, boolean debug = false) {
    super(context, okapiDomain, debug)
  }

  /**
   * Generates a URL for the specified path.
   *
   * @param path The path for which to generate the URL.
   * @return The generated URL.
   */
  String generateUrl(String path) {
    "https://${okapiDomain}${path}"
  }

  /**
   * Gets the default headers for the specified tenant.
   *
   * @param tenant The tenant for which to get the default headers.
   * @return The default headers.
   */
  static Map<String, String> getDefaultHeaders(OkapiTenant tenant) {
    return ["X-Okapi-Tenant": tenant.tenantId, "Content-Type": "application/json"]
  }

  /**
   * Gets the authorized headers for the specified tenant.
   *
   * @param tenant The tenant for which to get the authorized headers.
   * @return The authorized headers.
   */
  Map<String, String> getAuthorizedHeaders(OkapiTenant tenant) {
    getDefaultHeaders(tenant) + ["X-Okapi-Token": getOkapiToken(tenant)]
  }

  /**
   * Gets the token for the specified tenant.
   *
   * @param tenant The tenant for which to get the token.
   * @return token with headers.
   */
  String getToken(OkapiTenant tenant) {
    getDefaultHeaders(tenant) + ["X-Okapi-Token": getOkapiToken(tenant)]
  }

  /**
   * Gets the token with expiry for the specified tenant.
   *
   * @param tenant The tenant for which to get the token.
   * @return token with headers.
   */
  String getTokenWithExpiry(OkapiTenant tenant) {
    //TODO Need to test an ability to use X-Okapi-Token header with new endpoint. RANCHER-1082
    getDefaultHeaders(tenant) + ["X-Okapi-Token": getOkapiTokenWithExpiry(tenant)]
  }

  /**
   * Checks if the user credentials exist for the specified tenant.
   *
   * @param tenant The tenant for which to check the user credentials.
   * @param user The user for which to check the credentials.
   * @return True if the user credentials exist; false otherwise.
   */
  boolean checkUserCredentialsExist(OkapiTenant tenant, OkapiUser user) {
    user.checkUuid()

    String url = generateUrl("/authn/credentials-existence?userId=${user.uuid}")
    Map<String, String> headers = getAuthorizedHeaders(tenant)

    def response = restClient.get(url, headers).body

    return response?.credentialsExist
  }

  /**
   * Sets the user credentials for the specified tenant.
   *
   * @param tenant The tenant for which to set the user credentials.
   * @param user The user for which to set the credentials.
   */
  void setUserCredentials(OkapiTenant tenant, OkapiUser user) {
    if (checkUserCredentialsExist(tenant, user)) {
      logger.warning("User credentials already exist for user (${user.username}). Skipping credentials creation.")
      return
    }

    String url = generateUrl("/authn/credentials")
    Map<String, String> headers = getAuthorizedHeaders(tenant)
    Map<String, Object> body = [
      "password": user.getPasswordPlainText(),
      "userId"  : user.uuid
    ]

    restClient.post(url, body, headers)
    logger.info("Credentials for user ${user.username} created successfully")
  }

  /**
   * Logs in the user for the specified tenant.
   *
   * @param tenant The tenant for which to log in the user.
   */
  void loginUser(OkapiTenant tenant) {
    String url = generateUrl("/bl-users/login")
    Map<String, String> headers = getDefaultHeaders(tenant)
    Map<String, String> body = [
      "username": tenant.adminUser.username,
      "password": tenant.adminUser.getPasswordPlainText()
    ]

    def response = restClient.post(url, body, headers)

    if (response) {
      tenant.adminUser.token = response.headers['X-Okapi-Token']
      tenant.adminUser.uuid = response.body.user.id
      tenant.adminUser.permissions = response.body.permissions.permissions
      tenant.adminUser.permissionsId = response.body.permissions.permissionsId
    }
  }

  /**
   * Gets the Okapi token for the specified tenant.
   *
   * @param tenant The tenant for which to get the Okapi token.
   * @return The Okapi token.
   */
  String getOkapiToken(OkapiTenant tenant) {
    if (!tenant.adminUser) {
      logger.warning("Admin user not set for ${tenant.tenantId}")
      return
    }

    String url = generateUrl("/authn/login")
    Map<String, String> headers = getDefaultHeaders(tenant)
    Map<String, String> body = [
      "username": tenant.adminUser.username,
      "password": tenant.adminUser.getPasswordPlainText()
    ]

    try {
      def response = restClient.post(url, body, headers)

      if (response) {
        String token = response.headers['x-okapi-token'].join()
        tenant.adminUser.token = token
        return token
      }
    } catch (RequestException e) {
      if (e.statusCode != HttpURLConnection.HTTP_NOT_FOUND && e.statusCode != HttpURLConnection.HTTP_BAD_REQUEST) {
        throw new RequestException(e.getMessage(), e.statusCode)
      }
    }
  }

  /**
   * Gets the Okapi token with cookie structure and expiry date for the specified tenant.
   *
   * @param tenant The tenant for which to get the Okapi token.
   * @return The Okapi token with expiry.
   */
  String getOkapiTokenWithExpiry(OkapiTenant tenant) {
    if (!tenant.adminUser) {
      logger.warning("Admin user not set for ${tenant.tenantId}")
      return
    }

    String url = generateUrl("/bl-users/login-with-expiry")
    Map<String, String> headers = getDefaultHeaders(tenant)
    Map<String, String> body = [
      "username": tenant.adminUser.username,
      "password": tenant.adminUser.getPasswordPlainText()
    ]

    try {
      def response = restClient.post(url, body, headers)
      if (response) {
        def token_data = response.headers['Set-Cookie'][1]
        headers.put("Cookie", token_data)
        tenant.adminUser.cookie = headers
        return headers
      }
    } catch (RequestException e) {
      if (e.statusCode != HttpURLConnection.HTTP_NOT_FOUND && e.statusCode != HttpURLConnection.HTTP_BAD_REQUEST) {
        throw new RequestException(e.getMessage(), e.statusCode)
      }
    }
  }

  /**
   * Checks if the tenant is locked by trying to login with the tenant's admin credentials.
   *
   * @param tenant The OkapiTenant object representing the tenant.
   * @return Returns 'true' if the tenant is locked, otherwise returns 'false'.
   *         If the HTTP response code is other than 404 (NOT FOUND), an exception will be thrown.
   * @throws RequestException when there's an unexpected error during the REST call.
   */
  boolean isTenantLocked(OkapiTenant tenant) {
    // Construct the URL for the login endpoint.
    String url = generateUrl("/authn/login")

    // Set up default headers.
    Map<String, String> headers = getDefaultHeaders(tenant)

    // Prepare the request body using the tenant's admin credentials.
    Map<String, String> body = [
      "username": tenant.adminUser.username,
      "password": tenant.adminUser.getPasswordPlainText()
    ]

    try {
      // Attempt to POST the credentials to the login endpoint.
      restClient.post(url, body, headers)

      // If the POST succeeds, it means the tenant is locked.
      return true
    } catch (RequestException e) {
      // If the response is a 404 NOT FOUND, it means the tenant is not locked.
      if (e.statusCode == HttpURLConnection.HTTP_NOT_FOUND) {
        return false
      }
      // If there's another type of error, re-throw the exception.
      throw new RequestException(e.getMessage(), e.statusCode)
    }
  }
}
