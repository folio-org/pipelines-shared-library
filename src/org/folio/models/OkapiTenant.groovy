package org.folio.models

/**
 * OkapiTenant class representing a tenant configuration for Okapi.
 * It provides chainable setter methods following builder pattern for ease of use.
 */
class OkapiTenant {
  /** Tenant's identifier. */
  String tenantId

  /** Tenant's name. */
  String tenantName

  /** Description of the tenant. */
  String tenantDescription

  /** Administrator user of the tenant. */
  OkapiUser adminUser

  /** Modules that are installed for the tenant. */
  Modules modules

  /** Index information associated with the tenant. */
  Index index

  /** Parameters for installation requests for the tenant. */
  InstallRequestParams installRequestParams

  /** Okapi configuration for the tenant. */
  OkapiConfig okapiConfig

  /** User Interface (UI) details for the tenant. */
  TenantUi tenantUi

  /**
   * Constructor that sets the tenantId and initializes modules.
   * @param tenantId Tenant's identifier.
   */
  OkapiTenant(String tenantId) {
    this.tenantId = tenantId
    this.modules = new Modules()
  }

  /**
   * Chainable setter for tenant's name.
   * @param tenantName Name of the tenant.
   * @return The OkapiTenant object.
   */
  OkapiTenant withTenantName(String tenantName) {
    this.tenantName = tenantName
    return this
  }

  /**
   * Chainable setter for tenant's description.
   * @param tenantDescription Description of the tenant.
   * @return The OkapiTenant object.
   */
  OkapiTenant withTenantDescription(String tenantDescription) {
    this.tenantDescription = tenantDescription
    return this
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
   * Chainable setter for index information.
   * @param index Index information associated with the tenant.
   * @return The OkapiTenant object.
   */
  OkapiTenant withIndex(Index index) {
    this.index = index
    return this
  }

  /**
   * Chainable setter for install request parameters.
   * @param installRequestParams Parameters for installation requests for the tenant.
   * @return The OkapiTenant object.
   */
  OkapiTenant withInstallRequestParams(InstallRequestParams installRequestParams) {
    this.installRequestParams = installRequestParams
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

  /**
   * Chainable setter for tenant UI.
   * @param tenantUi User Interface (UI) details for the tenant.
   * @return The OkapiTenant object.
   */
  OkapiTenant withTenantUi(TenantUi tenantUi) {
    this.tenantUi = tenantUi
    this.tenantUi.tenantId = this.tenantId
    return this
  }
}
