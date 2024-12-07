package org.folio.models

import org.folio.models.module.EurekaModule

/**
 * Represents a Rancher Eureka namespace and its configuration.*/
class EurekaNamespace extends RancherNamespace {

  FolioInstallJson<EurekaModule> modules = new FolioInstallJson(EurekaModule.class)

  Map<String, EurekaTenant> tenants = [:]

  Map<String, String> applications = [:]

  boolean enableECS_CCL = false

  EurekaNamespace(String clusterName, String namespaceName) {
    super(clusterName, namespaceName)
  }

  EurekaNamespace withApplications(Map<String, String> apps){
    applications = apps
    return this
  }

  /**
   * Adds a tenant to the RancherNamespace.
   * @param tenant the OkapiTenant to add
   * @throws IllegalArgumentException if the tenant ID is "supertenant"
   */
  @Override
  void addTenant(OkapiTenant tenant) {
    super.addTenant(tenant)

    applications.putAll((tenant as EurekaTenant).applications)
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

  @Override
  void addDeploymentConfig(String branch = DEPLOYMENT_CONFIG_BRANCH) {
    super.addDeploymentConfig(branch)

    if (getDeploymentConfigType() && enableECS_CCL)
      setDeploymentConfig(mergeMaps(getDeploymentConfig(), getFeatureConfig('ecs-ccl', branch)))
  }

  private EurekaTenantConsortia findCentralConsortiaTenant() {
    return tenants.values().find {
      it instanceof EurekaTenantConsortia && it.isCentralConsortiaTenant
    } as EurekaTenantConsortia
  }
}
