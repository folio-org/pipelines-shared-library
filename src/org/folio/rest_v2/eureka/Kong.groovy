package org.folio.rest_v2.eureka

import org.folio.models.EurekaTenant
import org.folio.models.User

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

  Map<String, String> getTenantUserHttpHeaders(EurekaTenant tenant, User user = tenant.getAdminUser(), boolean addOkapiAuth = false) {
    return getDefaultHeaders() + keycloak.getAuthTenantHeaders(tenant, user, addOkapiAuth)
  }
}
