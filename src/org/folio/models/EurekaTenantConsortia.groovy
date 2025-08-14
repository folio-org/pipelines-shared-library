package org.folio.models

import com.cloudbees.groovy.cps.NonCPS
import org.folio.models.application.ApplicationList
import org.folio.models.module.EurekaModule

/**
 * OkapiTenantConsortia class is a subclass of OkapiTenant
 * representing a tenant configuration specifically for consortia.
 * It provides chainable setter methods following builder pattern for ease of use.
 */
class EurekaTenantConsortia extends EurekaTenant {
  /** Flag indicating if the tenant is the central consortia tenant. */
  boolean isCentralConsortiaTenant

  /** Name of the consortia. */
  String consortiaName

  /** UUID of the consortia. */
  String consortiaUuid

  /** Code associated with the tenant. */
  String tenantCode

  /** Identifier for the central tenant in the consortia. */
  String centralTenantId

  EurekaTenantConsortia(){}

  /**
   * Constructor that sets the tenantId, initializes modules, and sets the isCentralConsortiaTenant flag.
   * @param tenantId Tenant's identifier.
   * @param isCentralConsortiaTenant Boolean flag indicating if the tenant is the central consortia tenant.
   */
  EurekaTenantConsortia(String tenantId, boolean isCentralConsortiaTenant = false) {
    super(tenantId)
    this.isCentralConsortiaTenant = isCentralConsortiaTenant
  }

  /**
   * Chainable setter for tenant code.
   * @param tenantCode Code associated with the tenant.
   * @return The OkapiTenantConsortia object.
   */
  EurekaTenantConsortia withTenantCode(String tenantCode) {
    this.tenantCode = tenantCode
    return this
  }

  /**
   * Chainable setter for consortia name.
   * @param consortiaName Name of the consortia.
   * @return The OkapiTenantConsortia object.
   */
  EurekaTenantConsortia withConsortiaName(String consortiaName) {
    this.consortiaName = consortiaName
    return this
  }

  /**
   * Chainable setter for install JSON.
   * It removes 'folio_consortia-settings' module for non-central consortia tenants.
   * @param installJson The install JSON object.
   * @return The OkapiTenantConsortia object.
   */
  @Override
  EurekaTenantConsortia withInstallJson(List<Map<String, String>> installJson) {
    //TODO: Fix DTO convert issue with transformation from FolioInstallJson<FolioModule> to FolioInstallJson<EurekaModule>
    setModules(new FolioInstallJson(EurekaModule.class))

    this.getModules().setInstallJsonObject(installJson)
    return this
  }

  /**
   * Chainable setter for install query parameters.
   * It removes "loadSample" tenant parameter for non-central consortia tenants.
   * @param installRequestParams The InstallRequestParams object.
   * @return The OkapiTenantConsortia object.
   */
  EurekaTenantConsortia withInstallRequestParams(InstallRequestParams installRequestParams) {
    super.withInstallRequestParams(installRequestParams)
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
  EurekaTenantConsortia enableFolioExtensions(def script, List<String> extensions, boolean isRelease = false) {
    super.enableFolioExtensions(script, extensions, isRelease)
    // Remove the 'folio_consortia-settings, folio_ld-folio-wrapper, and mod-linked-data' module if this tenant is not
    // the central consortia tenant
    if (!this.isCentralConsortiaTenant) {
      this.getModules().removeModuleByName('folio_consortia-settings')
      this.getModules().removeModuleByName('folio_ld-folio-wrapper')
      this.getModules().removeModuleByName('mod-linked-data')
    }

    return this
  }

  /**
   * Assigns applications to the tenant based on specific conditions.
   * This method filters the provided applications and assigns them to the tenant.
   *
   * @param apps The list of applications to be assigned.
   * @return The EurekaTenantConsortia object for method chaining.
   */
  @Override
  EurekaTenantConsortia assignApplications(ApplicationList apps){
    ApplicationList appsToAssign = new ApplicationList()
    appsToAssign.addAll(apps)

    appsToAssign.removeAll {app -> (app.name == "app-consortia-manager" || app.name == "app-linked-data") && !isCentralConsortiaTenant  }

    return super.assignApplications(appsToAssign) as EurekaTenantConsortia
  }

  @NonCPS
  @Override
  String toString(){
    return """
      "class_name": "EurekaTenantConsortia",
      "uuid": "$uuid"
      "tenantId": "$tenantId",
      "tenantName": "$tenantName",
      "tenantDescription": "$tenantDescription",
      "isCentralConsortiaTenant" : "$isCentralConsortiaTenant",
      "consortiaName": "$consortiaName",
      "centralTenantId": "$centralTenantId",
      "consortiaUUID": "$consortiaUuid",
      "tenantCode": "$tenantCode",
      "applications": "$applications",
      "modules": ${modules.getInstallJsonObject()},
      "indexes": $indexes
    """
  }
}
