package org.folio.rest_v2.eureka

import org.folio.models.Tenant

class Kong<T extends Kong> extends Base {

  private Keycloak keycloak
  private String kongUrl

  protected Kong(def context, String kongUrl, Keycloak keycloak, boolean debug = false){
    super(context, debug)

    this.keycloak = keycloak
    this.kongUrl = kongUrl
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
    "https://${okapiDomain}${path}"
  }

  Map<String, String> getMasterHttpHeaders(boolean addOkapiAuth = false) {
    return getDefaultHeaders() + keycloak.getAuthMasterTenantHeaders(addOkapiAuth)
  }

  Map<String, String> getTenantHttpHeaders(Tenant tenant, boolean addOkapiAuth = false) {
    return getDefaultHeaders() + keycloak.getAuthTenantHeaders(tenant, addOkapiAuth)
  }

  static T get(def context, String kongUrl, String keycloakUrl, boolean debug = false) {
    return (T) new Kong(context, kongUrl, new Keycloak(context, keycloakUrl, debug), debug)
  }
}
