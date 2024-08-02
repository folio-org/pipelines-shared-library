package org.folio.models

class Tenant {
  /** Tenant's identifier. */
  String tenantId

  /** Tenant's name. */
  String tenantName

  /** Description of the tenant. */
  String tenantDescription

  /** Modules that are installed for the tenant. */
  Modules modules

  /** Index information associated with the tenant. */
  Index index

  /** Parameters for installation requests for the tenant. */
  InstallRequestParams installRequestParams

  /** User Interface (UI) details for the tenant. */
  TenantUi tenantUi

  /**
   * Constructor that sets the tenantId and initializes modules.
   * @param tenantId Tenant's identifier.
   */
  Tenant(String tenantId, String tenantDescription = "") {
    this.tenantId = tenantId
    this.tenantDescription = tenantDescription
    this.modules = new Modules()
  }

  /**
   * Chainable setter for tenant's name.
   * @param tenantName Name of the tenant.
   * @return The Tenant object.
   */
  Tenant withTenantName(String tenantName) {
    this.tenantName = tenantName
    return this
  }

  /**
   * Chainable setter for tenant's description.
   * @param tenantDescription Description of the tenant.
   * @return The Tenant object.
   */
  Tenant withTenantDescription(String tenantDescription) {
    this.tenantDescription = tenantDescription
    return this
  }

  /**
   * Chainable setter for index information.
   * @param index Index information associated with the tenant.
   * @return The Tenant object.
   */
  Tenant withIndex(Index index) {
    this.index = index
    return this
  }

  /**
   * Chainable setter for install request parameters.
   * @param installRequestParams Parameters for installation requests for the tenant.
   * @return The Tenant object.
   */
  Tenant withInstallRequestParams(InstallRequestParams installRequestParams) {
    this.installRequestParams = installRequestParams
    return this
  }

  /**
   * Chainable setter for tenant UI.
   * @param tenantUi User Interface (UI) details for the tenant.
   * @return The Tenant object.
   */
  Tenant withTenantUi(TenantUi tenantUi) {
    this.tenantUi = tenantUi
    this.tenantUi.tenantId = this.tenantId
    return this
  }
}
