package org.folio.rest_v2.eureka

import org.folio.models.EurekaTenant
import org.folio.rest_v2.Common
import org.folio.utilities.RequestException

class Kong extends Common {

  private Keycloak keycloak

  Kong(Object context, String kongUrl, String keycloakUrl, boolean debug = false) {
    super(context, kongUrl, debug)

    keycloak = new Keycloak(context, keycloakUrl, debug)
  }
}
