package org.folio.models

/**
 * EurekaTenant class representing a tenant configuration for Eureka.
 * It provides chainable setter methods following builder pattern for ease of use.
 */
class EurekaTenant extends Tenant {
  /**
   * Keycloak client identifier.
   */
  String clientId

  /**
   * Some secret phrase to authenticate in Keycloak.
   * Is stored in AWS SSM Parameter Store or Hashicorp Vault.
   */
  String clientSecret

  /**
   * Constructor that sets the tenantId and initializes modules.
   * @param tenantId Tenant's identifier.
   * @param clientId Keycloak client identifier.
   * @param clientSecret Some secret phrase to authenticate in Keycloak.
   */
  EurekaTenant(String tenantId, String tenantDescription, String clientId, String clientSecret) {
    super(tenantId, tenantDescription)
    this.clientId = clientId
    this.clientSecret = clientSecret
  }

  /**
   * Chainable setter for install JSON.
   * @param installJson The install JSON object.
   * @return The EurekaTenant object.
   */
  EurekaTenant withInstallJson(Object installJson) {
    this.modules.setInstallJson(installJson)
    return this
  }
}
