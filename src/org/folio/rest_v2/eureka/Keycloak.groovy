package org.folio.rest_v2.eureka

import groovy.text.StreamingTemplateEngine
import hudson.util.Secret
import org.folio.models.EurekaTenant
import org.folio.models.User

/**
 * The Keycloak class is responsible for various operations related to
 * user and tenant authorization and related to the Keycloak IAM/SSO product.
 * This includes generating headers, and tokens, checking user credentials,
 * setting user credentials, and logging in.
 */
class Keycloak extends Base {

  static String REALM_TOKEN_PATH_TEMPLATE = 'realms/${tenant}/protocol/openid-connect/token'
  static String MASTER_TENANT_CLIENT_ID = "folio-backend-admin-client"
  static Secret MASTER_TENANT_CLIENT_SECRET = Secret.fromString("SecretPassword")

  String keycloakURL

  /**
   * Initializes a new instance of the Authorization class.
   *
   * @param context The current context.
   * @param kongURL The api gateway (KONG) URL.
   * @param keycloakURL The SSO/IAM (KeyCloak) URL.
   * @param debug Debug flag indicating whether debugging is enabled.
   */
  Keycloak(def context, String keycloakURL, boolean debug = false) {
    super(context, debug)
    this.keycloakURL = keycloakURL
  }

  /**
   * Generates a URL for the specified path.
   *
   * @param path The path for which to generate the URL.
   * @return The generated URL.
   */
  String generateUrl(String path) {
    "https://${keycloakURL}${path}"
  }

  /**
   * Gets the authorized headers for the specified tenant.
   *
   * @param tenant The tenant for which to get the authorized headers.
   * @return The authorized headers.
   */
  static Map<String, String> getAuthorizedHeaders(String token, boolean addOkapiAuth = false) {
    return ['Authorization': "Bearer ${token}"] + (addOkapiAuth ? ["X-Okapi-Token": token] : [:]) as Map<String, String>
  }

  Map<String,String> getAuthMasterTenantHeaders(boolean addOkapiAuth = false) {
    return getAuthorizedHeaders(getAuthMasterTenantToken(), addOkapiAuth)
  }

  Map<String,String> getAuthTenantHeaders(EurekaTenant tenant, User user = null, boolean addOkapiAuth = false) {
    return getAuthorizedHeaders(getAuthTenantToken(tenant, user), addOkapiAuth) + ["X-Okapi-Tenant": tenant.tenantId]
  }

  String getAuthMasterTenantToken() {
    return getAuthToken("master", MASTER_TENANT_CLIENT_ID, MASTER_TENANT_CLIENT_SECRET)
  }

  String getAuthTenantToken(EurekaTenant tenant, User user = null) {
    return getAuthToken(
      tenant.tenantId
      , user ? "${tenant.tenantId}${tenant.userClientIdSuffix}" : tenant.clientId
      , user ? tenant.userClientSecret : tenant.clientSecret
      , user?.username, user?.password
    )
  }

  String getAuthToken(String tenantId, String clientId, Secret clientSecret, String username = null, Secret password = null){
    logger.info("Getting access token from Keycloak service for tenant $tenantId with client ID $clientId ${username ? "and username $username" : ""} ...")

    String url = generateUrl("/${getRealmTokenPath(tenantId)}")

    Map<String,String> headers = ['Content-Type':'application/x-www-form-urlencoded']

    String userCredentials = (username ? "&username=${username}" : "") +
      (password ? "&password=${password.getPlainText()}" : "")

    String grantType = (username && password) ? "&grant_type=password" : "&grant_type=client_credentials"

    String requestBody = "client_id=${clientId}&client_secret=${clientSecret.getPlainText()}${grantType}${userCredentials}"

    def response = restClient.post(url, requestBody, headers).body

    logger.info("Access token obtained successfully from Keycloak service")

    return response['access_token']
  }

  Keycloak defineTTL(String tenantId, int ttl = 300) {
    if (tenantId == 'master') {

      logger.info("Increasing TTL for tenant $tenantId ....")

      String url = generateUrl("/admin/realms/${tenantId}")

      Map<String, String> headers = ['Content-Type': 'application/json'] + getAuthMasterTenantHeaders()

      Map body = [
        "accessTokenLifespan"  : "$ttl",
        "ssoSessionIdleTimeout": "$ttl"
      ]

      restClient.put(url, body, headers).body

      logger.info("TTL for tenant $tenantId has been increased successfully to $ttl")

      return this
    }
  }

  static String getRealmTokenPath(String tenantId){
    return (new StreamingTemplateEngine()
      .createTemplate(REALM_TOKEN_PATH_TEMPLATE)
      .make(["tenant": tenantId])
    ).toString()
  }
}
