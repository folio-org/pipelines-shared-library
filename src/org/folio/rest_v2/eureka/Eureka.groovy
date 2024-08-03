package org.folio.rest_v2.eureka

import org.folio.models.Tenant
import org.folio.rest_v2.Common

class Eureka extends Common {

  private Kong kong

  Eureka(Object context, String kongUrl, String keycloakUrl, boolean debug = false) {
    super(context, kongUrl, debug)

    this.kong = new Kong(context, kongUrl, keycloakUrl, debug)
  }

  void initializeFromScratch(Map<String, Tenant> tenants, boolean enableConsortia) {
    tenants.each { tenantId, tenant ->
      kong.createTenant(tenant)
    }
  }
}
