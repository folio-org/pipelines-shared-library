package org.folio.rest_v2.eureka

import hudson.util.Secret
import org.folio.models.EurekaTenant

class Kong extends Base {

  protected Keycloak keycloak
  protected String kongUrl

  Kong(def context, String kongUrl, Keycloak keycloak, boolean debug = false) {
    super(context, debug)

    this.keycloak = keycloak
    this.kongUrl = kongUrl
  }

  Kong(def context, String kongUrl, String keycloakUrl, boolean debug = false) {
    this(context, kongUrl, new Keycloak(context, keycloakUrl, debug), debug)
  }

  /**
   * Gets the default headers.
   *
   * @return The default headers.
   */
  static Map<String, String> getDefaultHeaders() {
    return [
      "Content-Type": "application/json"
    ]
  }

  /**
   * Generates a URL for the specified path.
   *
   * @param path The path for which to generate the URL.
   * @return The generated URL.
   */
  String generateUrl(String path) {
    "https://${kongUrl}${path}"
  }

  Map<String, String> getMasterHttpHeaders(boolean addOkapiAuth = false) {
    return getDefaultHeaders() + keycloak.getAuthMasterTenantHeaders(addOkapiAuth)
  }

  Map<String, String> getTenantHttpHeaders(EurekaTenant tenant, boolean addOkapiAuth = false) {
    return getDefaultHeaders() + keycloak.getAuthTenantHeaders(tenant, addOkapiAuth)
  }

  Map<String, String> getTenantHttpHeaders(String tenantId, String token, boolean addOkapiAuth = false) {
    return getDefaultHeaders() + keycloak.getAuthorizedHeaders(token, addOkapiAuth) + ["X-Okapi-Tenant": tenantId]
  }

  String getAuthUserToken(String tenantId, String username, Secret password) {
    logger.info("Getting access token via login for user " + username)

    String url = generateUrl("/authn/login")

    Map<String, String> headers = ['Content-Type': 'application/json', 'x-okapi-tenant': tenantId]

    Map requestBody = ["username": username, "password": password.getPlainText()]

    def response = restClient.post(url, requestBody, headers).body

    logger.info("Access token obtained successfully via login for user " + username)

    return response['okapiToken']
  }
}
