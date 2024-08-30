package org.folio.models

/**
 * OkapiTenantConsortia class is a subclass of OkapiTenant
 * representing a tenant configuration specifically for consortia.
 * It provides chainable setter methods following builder pattern for ease of use.
 */
class OkapiTenantConsortia extends OkapiTenant {
  /** Flag indicating if the tenant is the central consortia tenant. */
  boolean isCentralConsortiaTenant

  /** Name of the consortia. */
  String consortiaName

  /** UUID of the consortia. */
  String consortiaUuid

  /** Code associated with the tenant. */
  String tenantCode

  OkapiTenantConsortia(){}

  /**
   * Constructor that sets the tenantId, initializes modules, and sets the isCentralConsortiaTenant flag.
   * @param tenantId Tenant's identifier.
   * @param isCentralConsortiaTenant Boolean flag indicating if the tenant is the central consortia tenant.
   */
  OkapiTenantConsortia(String tenantId, boolean isCentralConsortiaTenant = false) {
    super(tenantId)
    this.isCentralConsortiaTenant = isCentralConsortiaTenant
  }

  /**
   * Chainable setter for tenant code.
   * @param tenantCode Code associated with the tenant.
   * @return The OkapiTenantConsortia object.
   */
  OkapiTenantConsortia withTenantCode(String tenantCode) {
    this.tenantCode = tenantCode
    return this
  }

  /**
   * Chainable setter for consortia name.
   * @param consortiaName Name of the consortia.
   * @return The OkapiTenantConsortia object.
   */
  OkapiTenantConsortia withConsortiaName(String consortiaName) {
    this.consortiaName = consortiaName
    return this
  }

  /**
   * Chainable setter for install JSON.
   * It removes 'folio_consortia-settings' module for non-central consortia tenants.
   * @param installJson The install JSON object.
   * @return The OkapiTenantConsortia object.
   */
  OkapiTenantConsortia withInstallJson(Object installJson) {
    this.getModules().setInstallJson(installJson)
    if (!this.isCentralConsortiaTenant) {
      this.getModules().removeModule('folio_consortia-settings')
    }
    return this
  }

  /**
   * Chainable setter for install query parameters.
   * It removes "loadSample" tenant parameter for non-central consortia tenants.
   * @param installRequestParams The InstallRequestParams object.
   * @return The OkapiTenantConsortia object.
   */
  OkapiTenantConsortia withInstallRequestParams(InstallRequestParams installRequestParams) {
    super.withInstallRequestParams(installRequestParams)
    if (!this.isCentralConsortiaTenant) {
      this.getInstallRequestParams().removeTenantParameter("loadSample")
    }
    return this
  }
}
