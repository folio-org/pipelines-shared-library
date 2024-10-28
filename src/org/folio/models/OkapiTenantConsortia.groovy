package org.folio.models

/**
 * OkapiTenantConsortia class is a subclass of OkapiTenant
 * representing a tenant configuration specifically for consortia.
 * It provides chainable setter methods following the builder pattern for ease of use.
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

  /**
   * Constructor that sets the tenantId, initializes modules,
   * and sets the isCentralConsortiaTenant flag.
   *
   * @param tenantId Tenant's identifier.
   * @param isCentralConsortiaTenant Boolean flag indicating if the tenant is the central consortia tenant.
   */
  OkapiTenantConsortia(String tenantId, boolean isCentralConsortiaTenant = false) {
    super(tenantId)
    this.isCentralConsortiaTenant = isCentralConsortiaTenant
  }

  /**
   * Chainable setter for tenant code.
   * This method allows for setting the code associated with the tenant in a fluent manner.
   *
   * @param tenantCode Code associated with the tenant.
   * @return The OkapiTenantConsortia object for method chaining.
   */
  OkapiTenantConsortia withTenantCode(String tenantCode) {
    this.tenantCode = tenantCode
    return this
  }

  /**
   * Chainable setter for consortia name.
   * This method allows for setting the name of the consortia in a fluent manner.
   *
   * @param consortiaName Name of the consortia.
   * @return The OkapiTenantConsortia object for method chaining.
   */
  OkapiTenantConsortia withConsortiaName(String consortiaName) {
    this.consortiaName = consortiaName
    return this
  }

  /**
   * Chainable setter for installation request parameters.
   * This method allows for setting installation request parameters for the tenant.
   * It removes the "loadSample" tenant parameter for non-central consortia tenants.
   *
   * @param installRequestParams The InstallRequestParams object.
   * @return The OkapiTenantConsortia object for method chaining.
   */
  @Override
  OkapiTenantConsortia withInstallRequestParams(InstallRequestParams installRequestParams) {
    super.withInstallRequestParams(installRequestParams)
    // Remove the "loadSample" parameter if this tenant is not the central consortia tenant
    if (!this.isCentralConsortiaTenant) {
      this.getInstallRequestParams().removeTenantParameter("loadSample")
    }
    return this
  }

  /**
   * Enables specified Folio extensions for the consortia tenant.
   * This method retrieves the latest version of each specified extension module
   * and adds them to the tenant's installed modules. Additionally, it removes the
   * 'folio_consortia-settings' module if this tenant is not the central consortia tenant.
   *
   * @param script The Jenkins script context for accessing pipeline steps.
   * @param extensions List of extension IDs to enable.
   * @param isRelease Indicates whether to fetch the release version of the modules (default is false).
   */
  @Override
  void enableFolioExtensions(def script, List<String> extensions, boolean isRelease = false) {
    super.enableFolioExtensions(script, extensions, isRelease)
    // Remove the 'folio_consortia-settings' module if this tenant is not the central consortia tenant
    if (!this.isCentralConsortiaTenant) {
      this.getModules().removeModuleByName('folio_consortia-settings')
    }
  }
}
