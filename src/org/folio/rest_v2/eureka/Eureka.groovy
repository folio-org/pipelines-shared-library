package org.folio.rest_v2.eureka

import hudson.util.Secret
import org.folio.Constants
import org.folio.models.*
import org.folio.models.application.Application
import org.folio.models.application.ApplicationList
import org.folio.models.module.EurekaModule
import org.folio.models.module.FolioModule
import org.folio.rest_v2.eureka.kong.*

class Eureka extends Base {

  Kong kong

  Eureka(def context, String kongUrl, String keycloakUrl, boolean debug = false) {
    this(new Kong(context, kongUrl, keycloakUrl, debug))
  }

  Eureka(Kong kong) {
    super(kong.context, kong.getDebug())

    this.kong = kong
  }

  Eureka defineKeycloakTTL(int ttl = 600) {
    kong.keycloak.defineTTL("master", ttl)

    return this
  }

  Eureka createTenantFlow(EurekaTenant tenant, String cluster, String namespace
                          , boolean skipExistedType = false, boolean migrate = false) {
    EurekaTenant createdTenant = Tenants.get(kong).createTenant(tenant)

    tenant.withUUID(createdTenant.getUuid())
      .withClientSecret(retrieveTenantClientSecretFromAWSSSM(tenant))

    kong.keycloak.defineTTL(tenant.tenantId, 600)

    ApplicationList entitledApps = Tenants.get(kong).getEnabledApplications(tenant)

    Tenants.get(kong).enableApplications(
      tenant
      , tenant.applications
              .findAll { app -> !entitledApps.any { skipExistedType ? it.name == app.name : it.id == app.id } }
              .collect { it.id }
    )

    context.folioTools.stsKafkaLag(cluster, namespace, tenant.tenantId)

    //create tenant admin user
    createUserFlow(tenant, tenant.adminUser
      , new Role(name: "adminRole", desc: "Admin role")
      , Permissions.get(kong).getCapabilitiesId(tenant)
      , Permissions.get(kong).getCapabilitySetsId(tenant)
      , migrate)

    configureTenant(tenant)

    return this
  }

  Eureka configureTenant(EurekaTenant tenant){
    Configurations.get(kong)
      .setSmtp(tenant)
      .setResetPasswordLink(tenant)

    if(tenant.getModules().getModuleByName('mod-copycat'))
      Configurations.get(kong).setWorldcat(tenant)

    if(tenant.getModules().getModuleByName('mod-kb-ebsco-java'))
      Configurations.get(kong).setRmapiConfig(tenant)

    return this
  }

  /**
   * Retrieve Client Secret for the Tenant from AWS SSM parameter
   * @param EurekaTenant object
   * @return client secret as Secret object
   */
  Secret retrieveTenantClientSecretFromAWSSSM(EurekaTenant tenant) {
    context.awscli.withAwsClient {
      return Secret.fromString(
        context.awscli.getSsmParameterValue(Constants.AWS_REGION, tenant.secretStoragePathName, true) as String
      )
    }
  }

  Eureka createUserFlow(EurekaTenant tenant, User user, Role role, List<String> permissions, List<String> permissionSets, boolean migrate = false) {
    if (migrate) {
      Users.get(kong).invokeUsersMigration(tenant)
      UserGroups.get(kong).invokeGroupsMigration(tenant)
    }

    user.patronGroup.setUuid(
      UserGroups.get(kong)
        .createUserGroup(tenant, user.patronGroup)
        .getUuid()
    )

    user.setUuid(
      Users.get(kong)
        .createUser(tenant, user)
        .getUuid()
    )

    Users.get(kong).setUpdatePassword(tenant, user)

    role.setUuid(
      Permissions.get(kong)
        .createRole(tenant, role)
        .getUuid()
    )

    Permissions.get(kong).assignCapabilitiesToRole(
            tenant
            , role
            , permissions - Permissions.get(kong).getRoleCapabilitiesId(tenant, role)
    ).assignCapabilitySetsToRole(
            tenant
            , role
            , permissionSets - Permissions.get(kong).getRoleCapabilitySetsId(tenant, role)
    ).assignRolesToUser(tenant, user, [role])

    Users.get(kong).getAndAssignSPs(tenant, user)

    return this
  }

  ApplicationList generateApplications(Map<String, String> appNames, FolioInstallJson modules) {
    ApplicationList apps = []

    appNames.each { appName, appBranch ->
      apps.add(
        new Application()
          .withDescriptor(context.folioEurekaAppGenerator.generateFromRepository(appName, modules, appBranch, getDebug()) as Map)
      )
    }

    return apps
  }

  ApplicationList registerApplications(ApplicationList apps) {
    return apps.each {app -> Applications.get(kong).registerApplication(app.descriptor) }
  }

  Eureka registerModulesFlow(FolioInstallJson<? extends FolioModule> modules) {
    List<EurekaModule> alreadyRegistered = Applications.get(kong).getRegisteredModules()

    Applications.get(kong).registerModules(
      ((modules.getBackendModules() + modules.getEdgeModules()) as List<EurekaModule>)
        .findAll{
          // Exclude already registered modules to avoid error
          !alreadyRegistered.any{registered -> registered.getId() == it.getId() }
        }
    )

    return this
  }

  List<String> getApplicationModuleIds(Map<String, String> apps = null) {
    List<String> modules = []
    apps?.values()?.each { appId ->
      Applications.get(kong).getRegisteredApplicationDescriptors(appId).modules.each { module ->
        if (!modules.contains(module.id))
          modules.add(module.id)
      }
    }

    return modules.unique()
  }

  /**
   * Sets up a consortia with the given tenants.
   *
   * @param consortiaTenants A map of consortia tenants.
   */
  Eureka setUpConsortiaFlow(List<EurekaTenantConsortia> consortiaTenants) {
    EurekaTenantConsortia centralConsortiaTenant =
      consortiaTenants.find { it.isCentralConsortiaTenant }

    Consortia.get(kong).createConsortia(centralConsortiaTenant)

    Consortia.get(kong)
      .addCentralConsortiaTenant(centralConsortiaTenant)
      .checkConsortiaStatus(centralConsortiaTenant, centralConsortiaTenant)

    consortiaTenants.findAll { (!it.isCentralConsortiaTenant) }
      .each { institutionalTenant ->
        Consortia.get(kong)
          .addConsortiaTenant(centralConsortiaTenant, institutionalTenant)
          .checkConsortiaStatus(centralConsortiaTenant, institutionalTenant)
      }

    consortiaTenants.findAll { (!it.isCentralConsortiaTenant) }
      .each { institutionalTenant ->
        Consortia.get(kong)
          .addRoleToShadowAdminUser(centralConsortiaTenant, institutionalTenant, true)
      }

    return this
  }

  Eureka initializeFromScratch(Map<String, EurekaTenant> tenants, String cluster, String namespace
                               , boolean enableConsortia, boolean skipExistedAppType = false, boolean migrate = false) {
    tenants.each { tenantId, tenant ->
      createTenantFlow(tenant, cluster, namespace, skipExistedAppType, migrate)
    }

    if (enableConsortia)
      setUpConsortiaFlow(
        tenants.values().findAll {
          it instanceof EurekaTenantConsortia
        } as List<EurekaTenantConsortia>
      )

    tenants.each { tenantId, tenant ->
      if (tenant.indexes) {
        tenant.indexes.each { index ->
          Indexes.get(kong).runIndexFlow(tenant, index)
        }
      }
    }

    return this
  }

  /**
   * Get existed tenants.
   * @return Map of EurekaTenant objects.
   */
  Map<String, EurekaTenant> getExistedTenantsFlow(String namespace) {
    return Tenants.get(kong).getTenants().collectEntries {
      tenant ->
        tenant.withAWSSecretStoragePathName(namespace)
          .withClientSecret(retrieveTenantClientSecretFromAWSSSM(tenant))

        tenant.withApplications(Tenants.get(kong).getEnabledApplicationOnTenant(tenant, true))

        TenantConsortiaConfiguration consortiaConfig = Consortia.get(kong).getTenantConsortiaConfiguration(tenant)

        if(consortiaConfig){
          EurekaTenantConsortia consortiaTenant = tenant.convertTo(EurekaTenantConsortia.class)
          consortiaTenant.setIsCentralConsortiaTenant(consortiaConfig.centralTenantId == tenant.getTenantId())

          return [consortiaTenant.tenantName, consortiaTenant]
        }else {
          return [tenant.tenantName, tenant]
        }
    }
  }

  /**
   * Get existed tenants for a specific module.
   * @param namespace Namespace of the module.
   * @param moduleName Name of the module.
   * @return Map of EurekaTenant objects.
   */
  Map<String, EurekaTenant> getExistedTenantsForModule(String namespace, String moduleName) {
    return getExistedTenantsFlow(namespace).findAll {tenantName, tenant ->
      tenant.applications.byModuleName(moduleName)
    }
  }

  /**
   * Update Application Descriptor Flow.
   * @param applications Map of enabled applications in namespace.
   * @param modules EurekaModules object.
   * @param module EurekaModule object.
   * @return Map<AppName, AppID> of updated applications.
   */
  //TODO: Switch to the ApplicationList return type
  Map<String, String> updateAppDescriptorFlow(ApplicationList applications, EurekaModule module) {
    ApplicationList appWithDescriptors = new ApplicationList()

    applications.each { app ->
      appWithDescriptors.add(new Application(Applications.get(kong).getRegisteredApplicationDescriptors(app.id, true) as Map))
    }

    Map<String, String> updatedAppInfoMap = [:]

    appWithDescriptors.each { app ->

      String incrementalNumber = app.build + 1

      Map updatedAppDescriptor = getUpdatedApplicationDescriptor(app.descriptor, module, incrementalNumber)

      Applications.get(kong).registerApplication(updatedAppDescriptor)

      updatedAppInfoMap.put(updatedAppDescriptor['name'] as String, updatedAppDescriptor['id'] as String)
    }

    return updatedAppInfoMap
  }

  /**
   * Get Updated Application Descriptor with new Module Version
   * @param appDescriptor Current Application Descriptor as a Map
   * @param module Module object to be updated
   * @param buildNumber Build Number for new Application Version
   * @return Updated Application Descriptor as a Map
   */
  //TODO: Refactoring needed
  Map getUpdatedApplicationDescriptor(Map appDescriptor, EurekaModule module, String buildNumber) {
    // Update Application Descriptor with incremented Application Version
    String currentAppVersion = appDescriptor.version
    String newAppVersion = currentAppVersion.replaceFirst(/SNAPSHOT\.\d+/, "SNAPSHOT.${buildNumber}")

    appDescriptor.version = newAppVersion
    appDescriptor.id = "${appDescriptor.name}-${newAppVersion}"

    // Remove any URL links from previous module updates
    appDescriptor['modules'].each { it.containsKey('url') ? it.remove('url') : '' }

    // Update Application Descriptor with new Module Version
    for (item in appDescriptor['modules']) {
      if (item['name'] == module.name) {
        /** Module ID to update */
        String staleModuleId = item['id'] // save stale module id for descriptor removal

        // Update Module properties
        item['url'] = "${Constants.EUREKA_REGISTRY_DESCRIPTORS_URL}${module.name}-${module.version}"
        item['id'] = "${module.name}-${module.version}"
        item['version'] = module.version
        logger.info("Updated Module info:\n${item}")

        // Remove stale module descriptor from Updated Application Descriptor
        for (descriptor in appDescriptor['moduleDescriptors']) {
          if (descriptor['id'] == staleModuleId) {
            logger.info("Removing stale module descriptor \"${descriptor['id']}\" from Updated Application Descriptor")
            appDescriptor['moduleDescriptors'].remove(descriptor)
            break
          }
        }
        break
      }
    }

    logger.info("Updated Application Descriptor with new Module Version: ${module.name}-${module.version}\n")

    return appDescriptor as Map
  }

  /**
   * Remove Stale Resources Flow.
   * @param applications Map of enabled applications in namespace.
   * @param updatedApplications Map of updated applications in namespace.
   * @param module EurekaModule object.
   */
  //TODO: Remove this method
  void removeStaleResourcesFlow(ApplicationList configuredApps, Map<String, String> updatedApplications, EurekaModule module) {
    configuredApps.each { app ->
      if (updatedApplications.containsKey(app.name)) {

        if(Applications.get(kong).isModuleRegistered(module))
          Applications.get(kong).deleteRegisteredModule(module)

        Applications.get(kong).deleteRegisteredApplication(app.id)
      }
    }
  }

  /**
   * Remove Resources on Fail Flow.
   * @param updatedApplications Map of updated applications in namespace.
   * @param module EurekaModule object.
   */
  //TODO: Remove this method
  void removeResourcesOnFailFlow(Map<String, String> updatedApplications, EurekaModule module) {
    if (updatedApplications.isEmpty()) {
      logger.info("No updated applications found to remove resources.")
    } else {
      logger.info("Removing resources for failed module update...")

      updatedApplications.each { appName, appId ->
        if(Applications.get(kong).isModuleRegistered(module))
          Applications.get(kong).deleteRegisteredModule(module)

        Applications.get(kong).deleteRegisteredApplication(appId)

        logger.info("Removed resources for failed module update: ${appName}")
      }
    }
  }
}
