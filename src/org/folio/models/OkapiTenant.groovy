package org.folio.models

/**
 * OkapiTenant class representing a tenant configuration for Okapi.
 * It provides chainable setter methods following builder pattern for ease of use.
 */
class OkapiTenant extends Tenant {
  /**
   * Constructor that sets the tenantId and initializes modules.
   * @param tenantId Tenant's identifier.
   */
  OkapiTenant(String tenantId) {
    super(tenantId)
  }

  /**
   * Chainable setter for admin user.
   * @param adminUser Administrator user of the tenant.
   * @return The OkapiTenant object.
   */
  OkapiTenant withAdminUser(OkapiUser adminUser) {
    this.adminUser = adminUser
    return this
  }

  /**
   * Chainable setter for install JSON.
   * It removes 'mod-consortia' and 'folio_consortia-settings' modules.
   * @param installJson The install JSON object.
   * @return The OkapiTenant object.
   */
  OkapiTenant withInstallJson(Object installJson) {
    this.modules.setInstallJson(installJson)
    this.modules.removeModules(['mod-consortia', 'folio_consortia-settings'])
    return this
  }

  /**
   * Chainable setter for Okapi configuration.
   * It performs a deep copy of the configuration object.
   * @param config The OkapiConfig object.
   * @return The OkapiTenant object.
   */
  OkapiTenant withConfiguration(OkapiConfig okapiConfig) {
    this.okapiConfig = okapiConfig
    return this
  }
}
