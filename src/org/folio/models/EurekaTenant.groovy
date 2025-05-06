package org.folio.models

import com.cloudbees.groovy.cps.NonCPS
import hudson.util.Secret
import org.folio.models.application.ApplicationList
import org.folio.models.module.EurekaModule
import org.folio.utilities.Logger

/**
 * EurekaTenant class representing a tenant configuration for Eureka.
 * It provides chainable setter methods following builder pattern for ease of use.
 */
class EurekaTenant extends OkapiTenant {

  String uuid = ""

  /**
   * Keycloak client identifier.
   */
  String clientId = "sidecar-module-access-client"

  /**
   * Some secret phrase to authenticate in Keycloak.
   * Is stored in AWS SSM Parameter Store or Hashicorp Vault.
   */
  Secret clientSecret

  String secretStoragePathName

  /** Parameters for installation requests for the tenant. */
  EurekaRequestParams installRequestParams

  /** Modules that are installed for the tenant. */
  FolioInstallJson<EurekaModule> modules = new FolioInstallJson(EurekaModule.class)

  ApplicationList applications = new ApplicationList()

  boolean isSecureTenant = false

  EurekaTenant(){}

  /**
   * Constructor that sets the tenantId and initializes modules.
   * @param tenantId Tenant's identifier.
   */
  EurekaTenant(String tenantId) {
    super(tenantId)
  }

  EurekaTenant(String tenantId, String uuid) {
    super(tenantId)
    this.uuid = uuid
  }

  EurekaTenant withUUID(String uuid){
    this.uuid = uuid
    return this
  }

  EurekaTenant withClientSecret(Secret secret){
    clientSecret = secret
    return this
  }

  EurekaTenant withSecretStoragePathName(String path){
    secretStoragePathName = path
    return this
  }

  EurekaTenant withAWSSecretStoragePathName(String namespace){
    secretStoragePathName = "${namespace}_${tenantId}_${clientId}"
    return this
  }

  /**
   * Chainable setter for consortia secure flag.
   * @param isSecureTenant Flag indicating if the tenant is secure.
   * @return The EurekaTenant object.
   */
  EurekaTenant withSecureTenant(boolean isSecureTenant) {
    this.isSecureTenant = isSecureTenant
    return this
  }

  /**
   * Chainable setter for applications.
   * This method sets the applications for the tenant and adds their modules to the tenant's module list.
   *
   * @param apps The list of applications to be added.
   * @return The EurekaTenant object for method chaining.
   */
  EurekaTenant withApplications(ApplicationList apps){
    ApplicationList list = (apps instanceof ApplicationList)
      ? apps
      : new ApplicationList().tap { addAll(apps) }

    this.applications = list

    list.each { app ->
      app.modules.each {modules.addModule(it) }
    }

    return this
  }

  /**
   * Chainable setter for applications.
   * This method adds the provided applications to the tenant's application list and updates the modules accordingly.
   *
   * @param apps The list of applications to be added.
   * @return The EurekaTenant object for method chaining.
   */
  EurekaTenant addApplications(ApplicationList apps){
    applications.addAll(apps)

    apps.each { app ->
      app.modules.each {modules.addModule(it) }
    }

    return this
  }

  /**
   * Assigns applications to the tenant based on specific conditions.
   * This method filters the provided applications and assigns them to the tenant.
   *
   * @param apps The list of applications to be assigned.
   * @return The EurekaTenant object for method chaining.
   */
  EurekaTenant assignApplications(ApplicationList apps){
    ApplicationList appsToAssign = new ApplicationList()
    appsToAssign.addAll(apps)

    appsToAssign.removeAll {app ->
      switch (app.name) {
        case "app-requests-mediated-ui":
          return !isSecureTenant
          break
        case ["app-consortia", "app-consortia-manager"]:
          return !(this instanceof EurekaTenantConsortia)
          break
        default:
          return false
          break
      }
    }

    return addApplications(appsToAssign)
  }

  /**
   * Chainable setter for install JSON.
   * This method sets the installation JSON object while ensuring that specific
   * modules ('mod-consortia' and 'folio_consortia-settings') are removed.
   *
   * @param installJson The install JSON object.
   * @return The OkapiTenant object for method chaining.
   */
  @Override
  EurekaTenant withInstallJson(List<Map<String, String>> installJson) {
    //TODO: Fix DTO convert issue with transformation from FolioInstallJson<FolioModule> to FolioInstallJson<EurekaModule>
    modules = new FolioInstallJson(EurekaModule.class)

    super.withInstallJson(installJson)

    this.modules.removeModulesByName(['mod-consortia-keycloak', 'folio_consortia-settings'])
    return this
  }

  Map toMap(){
    Map ret = [
      name: tenantId,
      description: tenantDescription,
      secure: isSecureTenant
    ]

    if(tenantId.trim())
      ret.put("id", uuid)

    return ret
  }

  static EurekaTenant getTenantFromContent(Map content){
    return new EurekaTenant(content.name as String, content.id as String)
      .withTenantName(content.name as String)
      .withTenantDescription(content.description as String) as EurekaTenant
  }

  @NonCPS
  @Override
  String toString(){
    return """
      "class_name": "EurekaTenant",
      "uuid": "$uuid"
      "tenantId": "$tenantId",
      "tenantName": "$tenantName",
      "tenantDescription": "$tenantDescription",
      "isSecureTenant": "$isSecureTenant",
      "applications": "$applications",
      "modules": ${modules.getInstallJsonObject()},
      "indexes": $indexes
    """
  }
}
