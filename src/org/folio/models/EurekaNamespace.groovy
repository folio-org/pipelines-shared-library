package org.folio.models

import org.folio.models.module.EurekaModule

/**
 * Represents a Rancher Eureka namespace and its configuration.*/
class EurekaNamespace extends RancherNamespace {

  FolioInstallJson<EurekaModule> modules = new FolioInstallJson(EurekaModule.class)

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
}
