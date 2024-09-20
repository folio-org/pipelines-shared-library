package org.folio.rest_v2.eureka.kong

import org.folio.rest_v2.eureka.Keycloak

class EdgeUsers extends Users{
  EdgeUsers(Object context, String kongUrl, Keycloak keycloak, boolean debug) {
    super(context, kongUrl, keycloak, debug)
  }

  private String EUREKA_EDGE_USERS_CONF = 'edge/config_eureka.yaml'

}
