package org.folio.models

/**
 * EurekaTenant class representing a tenant configuration for Eureka.
 * It provides chainable setter methods following builder pattern for ease of use.
 */
class EurekaTenant extends Tenant {
  /**
   * Keycloak client identifier.
   * Deafault: 'sidecar-module-access-client'
   */
  String clientId

  /**
   * Some secret phrase to authenticate in Keycloak.
   * Is stored in AWS SSM Parameter Store or Hashicorp Vault.
   */
  String clientSecret

  /**
   * Keycloak service URL.
   * Is the same for all Tenants
   */
  String keycloakUrl

  /**
   * Kong service URL.
   * Is the same for all Tenants
   */
  String kongUrl

  /**
   * Tenant Manager Service URL.
   * Is the same for all Tenants.
   */
  String tenantManagerUrl

  /**
   * Constructor that sets the tenantId and initializes modules.
   * @param tenantId Tenant's identifier.
   * @param clientId Keycloak client identifier.
   * @param clientSecret Some secret phrase to authenticate in Keycloak.
   * @param keycloakUrl Keycloak service URL.
   * @param kongUrl Kong service URL.
   */
  EurekaTenant(String tenantId, String tenantDescription, String clientId, String clientSecret, String keycloakUrl, String kongUrl) {
    super(tenantId, tenantDescription)
    this.clientId = clientId
    this.clientSecret = clientSecret
    this.keycloakUrl = keycloakUrl
    this.kongUrl = kongUrl
    this.tenantManagerUrl = "${kongUrl}/tenants"
  }
}
