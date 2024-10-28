package org.folio.models

/**
 * Represents a Rancher Eureka namespace and its configuration.*/
class EurekaNamespace extends RancherNamespace {

  EurekaModules modules = new EurekaModules()

  Map<String, EurekaTenant> tenants = [:]

  Map<String, String> applications = [:]

  EurekaNamespace(String clusterName, String namespaceName) {
    super(clusterName, namespaceName)
  }

  EurekaNamespace withApplications(Map<String, String> apps){
    applications = apps
    return this
  }

  /**
   * Updates the configuration for consortia tenants in the RancherNamespace.*/
  @Override
  protected void updateConsortiaTenantsConfig() {
    EurekaTenantConsortia centralConsortiaTenant = findCentralConsortiaTenant()

    if (centralConsortiaTenant) {
      tenants.values().findAll { it instanceof EurekaTenantConsortia && !it.isCentralConsortiaTenant }
        .each { tenant ->
          tenant.okapiConfig.resetPasswordLink = centralConsortiaTenant.okapiConfig.resetPasswordLink
        }
    }
  }

  private EurekaTenantConsortia findCentralConsortiaTenant() {
    return tenants.values().find {
      it instanceof EurekaTenantConsortia && it.isCentralConsortiaTenant
    } as EurekaTenantConsortia
  }

  @Override
  void setEnableConsortia(boolean enableConsortia, boolean releaseVersion = false) {
    this.modules.addModules([this.modules.getModuleVersion('mod-consortia-keycloak', releaseVersion),
                             this.modules.getModuleVersion('folio_consortia-settings', releaseVersion)])
    this.enableConsortia = enableConsortia
  }
}
