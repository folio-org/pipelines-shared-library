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
  // admin-cli is Keycloak's built-in public client; always present in master realm
  static String KEYCLOAK_ADMIN_CLI_CLIENT_ID = "admin-cli"

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

  String getAuthToken(String tenantId, String clientId, Secret clientSecret = null, String username = null, Secret password = null){
    logger.info("Getting access token from Keycloak service for tenant $tenantId with client ID $clientId ${username ? "and username $username" : ""} ...")

    String url = generateUrl("/${getRealmTokenPath(tenantId)}")

    Map<String,String> headers = ['Content-Type':'application/x-www-form-urlencoded']

    String userCredentials = (username ? "&username=${username}" : "") +
      (password ? "&password=${password.getPlainText()}" : "")

    String grantType = (username && password) ? "&grant_type=password" : "&grant_type=client_credentials"

    // Omit client_secret for public clients (e.g. admin-cli); include it only when provided
    String clientSecretParam = (clientSecret && clientSecret.getPlainText()) ? "&client_secret=${clientSecret.getPlainText()}" : ""

    String requestBody = "client_id=${clientId}${clientSecretParam}${grantType}${userCredentials}"

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

  /**
   * Fixes the Keycloak auth flow by deleting the broken folio-backend-admin-client
   * and restarting the Keycloak StatefulSet.
   *
   * Uses the built-in admin-cli public client (password grant) to bootstrap auth,
   * because folio-backend-admin-client may be absent or have invalid credentials —
   * which is exactly the condition this method is meant to repair.
   *
   * @param namespaceName   The Kubernetes namespace where Keycloak is running.
   * @param adminUsername   Keycloak master-realm admin username (typically 'admin').
   * @param adminPassword   Keycloak master-realm admin password.
   */
  Keycloak fixAuth403error(String clusterName, String namespaceName, String adminUsername, Secret adminPassword) {
    logger.info("Fixing Keycloak auth flow in namespace $namespaceName ....")

    // Bootstrap with admin-cli (public client, always present) instead of
    // folio-backend-admin-client, which may be the broken client we're about to delete.
    String adminToken = getAuthToken("master", KEYCLOAK_ADMIN_CLI_CLIENT_ID, null, adminUsername, adminPassword)
    Map<String, String> headers = ['Content-Type': 'application/json'] + getAuthorizedHeaders(adminToken)

    String getClientUrl = generateUrl("/admin/realms/master/clients?clientId=${MASTER_TENANT_CLIENT_ID}")

    List clients = restClient.get(getClientUrl, headers).body as List
    if (clients && !clients.isEmpty()) {
      String clientUuid = clients.first()['id'] as String
      String deleteClientUrl = generateUrl("/admin/realms/master/clients/${clientUuid}")
      restClient.delete(deleteClientUrl, headers).body
      logger.info("Client ${MASTER_TENANT_CLIENT_ID} has been deleted from master realm")
    } else {
      logger.info("Client ${MASTER_TENANT_CLIENT_ID} was not found in master realm")
    }

    context.folioHelm.withKubeConfig(clusterName) {
      String statefulSetNames = context.kubectl.getKubernetesStsNames(namespaceName)
      String keycloakStatefulSet = statefulSetNames
        .tokenize(' ')
        .find { it.contains('keycloak') }

      if (!keycloakStatefulSet) {
        throw new Exception("Keycloak statefulset was not found in namespace ${namespaceName}")
      }

      String replicaCount = context.kubectl.getKubernetesResourceCount('statefulset', keycloakStatefulSet, namespaceName).trim()

      context.kubectl.setKubernetesResourceCount('statefulset', keycloakStatefulSet, namespaceName, '0')
      context.kubectl.setKubernetesResourceCount('statefulset', keycloakStatefulSet, namespaceName, replicaCount)
      context.kubectl.waitPodIsRunning(namespaceName, "${keycloakStatefulSet}-0")
    }

    logger.info("Keycloak statefulset in namespace ${namespaceName} has been restarted and is running")

    return this
  }

  static String getRealmTokenPath(String tenantId){
    return (new StreamingTemplateEngine()
      .createTemplate(REALM_TOKEN_PATH_TEMPLATE)
      .make(["tenant": tenantId])
    ).toString()
  }
}
