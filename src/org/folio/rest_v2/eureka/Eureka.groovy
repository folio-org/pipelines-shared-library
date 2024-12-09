package org.folio.rest_v2.eureka

import hudson.util.Secret
import org.folio.Constants
import org.folio.models.*
import org.folio.models.module.EurekaModule
import org.folio.rest_v2.eureka.kong.*
import org.folio.utilities.RequestException

class Eureka extends Base {

  static Map<String, String> CURRENT_APPLICATIONS = [
    "app-platform-full"      : "snapshot"
    , "app-consortia"        : "snapshot"
    , "app-consortia-manager": "master"
    , "app-linked-data"      : "snapshot"
  ]

  static Map<String, String> CURRENT_APPLICATIONS_WO_CONSORTIA = [
    "app-platform-full": "snapshot"
    , "app-linked-data": "snapshot"
  ]

  private Kong kong

  Eureka(def context, String kongUrl, String keycloakUrl, boolean debug = false) {
    this(new Kong(context, kongUrl, keycloakUrl, debug))
  }

  Eureka(Kong kong) {
    super(kong.context, kong.getDebug())

    this.kong = kong
  }

  Eureka defineKeycloakTTL(int ttl = 3600) {
    kong.keycloak.defineTTL("master", ttl)

    return this
  }

  Eureka createTenantFlow(EurekaTenant tenant, String cluster, String namespace) {
    EurekaTenant createdTenant = Tenants.get(kong).createTenant(tenant)

    tenant.withUUID(createdTenant.getUuid())
      .withClientSecret(retrieveTenantClientSecretFromAWSSSM(tenant))

    kong.keycloak.defineTTL(tenant.tenantId, 3600)

    Tenants.get(kong).enableApplicationsOnTenant(tenant)

    context.folioTools.stsKafkaLag(cluster, namespace, tenant.tenantId)

    //create tenant admin user
    createUserFlow(tenant, tenant.adminUser
      , new Role(name: "adminRole", desc: "Admin role")
      , Permissions.get(kong).getCapabilitiesId(tenant)
      , Permissions.get(kong).getCapabilitySetsId(tenant))

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

  Eureka createUserFlow(EurekaTenant tenant, User user, Role role, List<String> permissions, List<String> permissionSets) {
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

    Permissions.get(kong).assignCapabilitiesToRole(tenant, role, permissions, true)
      .assignCapabilitySetsToRole(tenant, role, permissionSets, true)
      .assignRolesToUser(tenant, user, [role])

    Users.get(kong).getAndAssignSPs(tenant, user)

    return this
  }

  Map<String, String> registerApplications(Map<String, String> appNames, Map<String, String> modules) {
    Map<String, String> apps = [:]

    appNames.each { appName, appBranch ->
      def jsonAppDescriptor = context.folioEurekaAppGenerator.generateApplicationDescriptor(appName, modules, appBranch, getDebug())

      apps.put(appName, Applications.get(kong).registerApplication(jsonAppDescriptor))
    }

    return apps
  }

  Eureka assignAppToTenants(List<EurekaTenant> tenants, Map<String, String> registeredApps) {
    tenants.each { tenant ->
      tenant.applications = registeredApps.clone() as Map

      //TODO: Refactoring is needed!!! Utilization of extension should be applied.
      if (!(tenant instanceof EurekaTenantConsortia)) {
        tenant.applications.remove("app-consortia")
        tenant.applications.remove("app-consortia-manager")
      } else if (!tenant.isCentralConsortiaTenant) {
        tenant.applications.remove("app-consortia-manager")
        tenant.applications.remove("app-linked-data")
      }
    }

    return this
  }

  Map<String, String> registerApplicationsFlow(Map<String, String> appNames
                                               , Map<String, String> modules
                                               , List<EurekaTenant> tenants) {

    Map<String, String> registeredApps = registerApplications(appNames, modules)

    assignAppToTenants(tenants, registeredApps)

    return registeredApps
  }

  Eureka registerModulesFlow(FolioInstallJson<EurekaModule> modules, Map<String, String> apps) {
    Applications.get(kong).registerModules(
      [
        "discovery": modules.getDiscoveryList(getApplicationModules(apps))
      ]
    )

    return this
  }

  List<String> getApplicationModules(Map<String, String> apps) {
    List<String> modules = []
    apps.values().each { appId ->
      Applications.get(kong).getRegisteredApplication(appId).modules.each { module ->
        if (!modules.contains(module.id))
          modules.add(module.id)
      }
    }

    return modules
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

    return this
  }

  Eureka initializeFromScratch(Map<String, EurekaTenant> tenants, String cluster, String namespace, boolean enableConsortia) {
    tenants.each { tenantId, tenant -> createTenantFlow(tenant, cluster, namespace) }

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
   * Get existed tenants where specific module is resides.
   * @param module Module Name to filter.
   * @return Map of EurekaTenant objects.
   */
  Map<String, EurekaTenant> getExistedTenantsForModule(EurekaModule module) {
    Map<String, EurekaTenant> tenants = Tenants.get(kong).getTenants().collectEntries {
      tenant -> [tenant.tenantName, tenant]
    }

    Map<String, EurekaTenant> tenantsForDeletion = [:]

    // Get enabled (entitled) applications for configured Tenants
    tenants.each { tenantName, tenant ->

      logger.debug("I'm in Eureka. Tenant $tenantName Start.")

      // Get applications where the passed module exists
      Map<String, Map> applications = Tenants.get(kong).getEnabledApplications(tenant, "", true)
        .findAll{appId, entitlement ->
          entitlement.modules.findAll{ moduleId -> moduleId =~ /${module.getName()}-\d+\..*/ }.size() > 0
        }

      logger.debug("I'm in Eureka. Tenant $tenantName After getEnabledApplications")

      if(applications.isEmpty()){
        tenantsForDeletion.put(tenantName, tenant) //let's delete it later from the tenant list
        return
      }

      logger.debug("I'm in Eureka. Tenant $tenantName After applications.isEmpty()")

      // Update tenant application list
      tenant.applications = applications.collectEntries { appId, entitlement ->
        [appId.split("-\\d+\\.\\d+\\.\\d+")[0], appId]
      } as Map<String, String>

      // Update tenant module list
      applications.each { appId, entitlement ->
        entitlement.modules.each {
          moduleId -> tenant.getModules().addModule(moduleId as String)
        }
      }

      logger.debug("I'm in Eureka. Tenant $tenantName Before tenant.getModules().addModule")

      tenant.getModules().addModule(module.getId())
    }

    logger.debug("I'm in Eureka. After tenants handling")
    logger.debug("I'm in Eureka. tenants: $tenants")
    logger.debug("I'm in Eureka. return:")
    logger.debug(tenants.collectEntries { tenantName, tenantDetails ->
      (!tenantsForDeletion.containsKey(tenantName)) ? [tenantName, tenantDetails] : null
    })

    logger.debug("I'm in Eureka. Finish")

    return tenants.collectEntries { tenantName, tenantDetails ->
        (!tenantsForDeletion.containsKey(tenantName)) ? [tenantName, tenantDetails] : null
      } as Map<String, EurekaTenant>
  }

  /**
   * Get Existed Applications on Environment Namespace.
   * @return Map of Application Name and Application ID.
   */
  static Map<String, String> getEnabledApplications(Map<String, EurekaTenant> tenants) {
    /** Enabled Applications in Environment */
    Map<String, String> enabledAppsMap = [:]

    // Get enabled applications from EurekaTenant List
    tenants.values().each { tenant ->
      tenant.applications.each { appName, appId ->
        enabledAppsMap.put(appName, appId)
      }
    }

    return enabledAppsMap
  }

  /**
   * Update Application Descriptor Flow.
   * @param applications Map of enabled applications in namespace.
   * @param modules EurekaModules object.
   * @param module EurekaModule object.
   * @return Map<AppName, AppID> of updated applications.
   */
  Map<String, String> updateAppDescriptorFlow(Map<String, String> applications, EurekaModule module) {
    /** Enabled Application Descriptors Map */
    Map<String, Object> appDescriptorsMap = [:]

    //Get application descriptors for enabled applications in namespace
    applications.each { appName, appId ->
      def appDescriptor = Applications.get(kong).getRegisteredApplication(appId, true)
      if (appDescriptor['modules'].any { it['name'] == module.name }) {
        appDescriptorsMap.put(appId, appDescriptor)
      }
    }

    /** Updated Application Info, Map<AppName, AppID> */
    Map<String, String> updatedAppInfoMap = [:]

    // Get Application Descriptor Updated with New Module Version
    appDescriptorsMap.each { appId, descriptor ->
      // Get Incremental Number for New Application Version
      String incrementalNumber = descriptor['version'].toString().tokenize('.').last().toInteger() + 2

      // Update existing Application Descriptor with New Module Version
      Map updatedAppDescriptor = getUpdatedApplicationDescriptor(descriptor as Map, module, incrementalNumber)

      // Print Updated Application Descriptor for Debugging
      //logger.info("Updated Application Descriptor to register:\n${prettyPrint(toJson(updatedAppDescriptor))}")

      // Register Updated Application Descriptor to Environment
      Applications.get(kong).registerApplication(updatedAppDescriptor)

      // Collect Updated Application information to Map<AppName, AppID>
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
        item['url'] = "${Constants.EUREKA_REGISTRY_URL}${module.name}-${module.version}"
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
   * Run Module Discovery Flow.
   * @param module EurekaModule object to discover
   */
  void runModuleDiscoveryFlow(EurekaModule module) {
    try {
      logger.info("Check if ${module.name}-${module.version} module discovery exists...")
      Applications.get(kong).getModuleDiscovery(module)
    } catch (RequestException e) {
      if (e.statusCode == HttpURLConnection.HTTP_NOT_FOUND) {
        Applications.get(kong).createModuleDiscovery(module)
      }
    }
  }

  /**
   * Enable Applications on Tenants Flow.
   * @param tenants Map of EurekaTenant objects.
   * @param appsToEnableMap Map<AppName, AppID> of registered application information.
   */
  void enableApplicationsOnTenantsFlow(Map<String, EurekaTenant> tenants, Map<String, String> appsToEnableMap) {
    tenants.each { tenantName, tenant ->
      // Upgrade Applications on Tenant
      Applications.get(kong).upgradeTenantApplication(tenant, appsToEnableMap)
    }
  }

  /**
   * Remove Stale Resources Flow.
   * @param applications Map of enabled applications in namespace.
   * @param updatedApplications Map of updated applications in namespace.
   * @param module EurekaModule object.
   */
  void removeStaleResourcesFlow(Map<String, String> configuredApps, Map<String, String> updatedApplications, EurekaModule module) {
    // Remove Previous Application Descriptor with Stale Module Version
    configuredApps.each { appName, appId ->
      if (updatedApplications.containsKey(appName)) {

        // Get Previous Module Version Discovery removed
        Applications.get(kong).searchModuleDiscovery("name==${module.name}")['discovery']?.each { moduleDiscovery ->
          if (moduleDiscovery['id'] != module.id) { // Remove only for the previous module versions
            Applications.get(kong).deleteModuleDiscovery(moduleDiscovery['id'] as String)
          }
        }

        // Delete Application Descriptor
        Applications.get(kong).deleteRegisteredApplication(appId)

      }
    }
  }

  /**
   * Remove Resources on Fail Flow.
   * @param updatedApplications Map of updated applications in namespace.
   * @param module EurekaModule object.
   */
  void removeResourcesOnFailFlow(Map<String, String> updatedApplications, EurekaModule module) {
    if (updatedApplications.isEmpty()) {
      logger.info("No updated applications found to remove resources.")
    } else {
      logger.info("Removing resources for failed module update...")

      // Remove Updated Application Descriptor with New Module Version
      updatedApplications.each { appName, appId ->
        // Get Updated Module Discovery removed
        Applications.get(kong).searchModuleDiscovery("name==${module.name}")['discovery']?.each { moduleDiscovery ->
          if (moduleDiscovery['id'] == module.id) { // Remove only for the updated module versions
            Applications.get(kong).deleteModuleDiscovery(moduleDiscovery['id'] as String)
          }
        }

        // Delete Application Descriptor
        Applications.get(kong).deleteRegisteredApplication(appId)

        logger.info("Removed resources for failed module update: ${appName}")
      }
    }
  }
}
