package org.folio.models

import com.cloudbees.groovy.cps.NonCPS
import org.folio.models.application.ApplicationList
import org.folio.models.module.EurekaModule
import org.folio.rest_v2.eureka.Eureka

/**
 * Represents a Rancher Eureka namespace and its configuration.*/
class EurekaNamespace extends RancherNamespace {

  FolioInstallJson<EurekaModule> modules = new FolioInstallJson(EurekaModule.class)

  Map<String, EurekaTenant> tenants = [:]

  ApplicationList applications = new ApplicationList()

  boolean enableECS_CCL = false

  boolean hasSecureTenant = false

  EurekaTenant secureTenant

  EurekaNamespace(String clusterName, String namespaceName) {
    super(clusterName, namespaceName)
  }

  EurekaNamespace withApplications(ApplicationList apps){
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

    if ((tenant as EurekaTenant).isSecureTenant) {
      secureTenant = tenant as EurekaTenant
      hasSecureTenant = (tenant as EurekaTenant).isSecureTenant
    }

    applications.addAll((tenant as EurekaTenant).applications)
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

  @Override
  EurekaNamespace instantiate(def context, boolean debug = false) {
    Eureka eureka = new Eureka(context, generateDomain('kong'), generateDomain('keycloak'))

    eureka
      .getExistedTenantsFlow("${getClusterName()}-${getNamespaceName()}")
      .values().each {addTenant(it)}

    context.folioHelm.withKubeConfig(getClusterName()) {
      List<String> coreModules = []

      modules.getBackendModules().each { module ->
        coreModules.add(context.kubectl.getDeploymentContainerImageName(getNamespaceName(), module.getName(), "sidecar"))
      }

      coreModules.add(context.kubectl.getDeploymentContainerImageName(getNamespaceName(), "kong-${getNamespaceName()}"))
      coreModules.add(context.kubectl.getStatefulSetContainerImageName(getNamespaceName(), "keycloak-${getNamespaceName()}"))
      coreModules.add(context.kubectl.getDeploymentContainerImageName(getNamespaceName(), "mgr-applications"))
      coreModules.add(context.kubectl.getDeploymentContainerImageName(getNamespaceName(), "mgr-tenants"))
      coreModules.add(context.kubectl.getDeploymentContainerImageName(getNamespaceName(), "mgr-tenant-entitlements"))

      coreModules
        .findAll{it?.trim()}
        ?.unique()
        ?.each {modules.addModule(it.replace(':', '-'), 'enabled')}
    }

    return this
  }

  private EurekaTenantConsortia findCentralConsortiaTenant() {
    return tenants.values().find {
      it instanceof EurekaTenantConsortia && it.isCentralConsortiaTenant
    } as EurekaTenantConsortia
  }

  @NonCPS
  @Override
  String toString(){
    return """
      "class_name": "EurekaNamespace",
      "namespace": "${getNamespaceName()}",
      "applications": "$applications",
      "modules": ${modules.getInstallJsonObject()},
      "enableECS_CCL": "$enableECS_CCL",
      "hasSecureTenant": "$hasSecureTenant",
      "secureTenant": "$secureTenant",
      "tenants": "$tenants"
    """
  }
}
